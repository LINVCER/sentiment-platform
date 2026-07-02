import scrapy
import redis
import json
import urllib.parse
import re
from datetime import datetime
from scrapy_collector.items import PostItem

class XhsSpider(scrapy.Spider):
    name = "xhs"
    allowed_domains = ["xiaohongshu.com"]

    def __init__(self, keyword=None, *args, **kwargs):
        super(XhsSpider, self).__init__(*args, **kwargs)
        self.keyword = keyword
        self.saved_count = 0

    def get_redis_cookies(self):
        try:
            r = redis.Redis(host='localhost', port=6379, db=0)
            cookies_json = r.get('xhs:cookies')
            if cookies_json:
                cookies_list = json.loads(cookies_json.decode('utf-8'))
                cookies_dict = {c['name']: c['value'] for c in cookies_list}
                self.logger.info(f"Successfully loaded {len(cookies_dict)} XHS cookies from Redis")
                return cookies_dict
        except Exception as e:
            self.logger.error(f"Failed to load cookies from Redis: {e}")
        return None

    async def start(self):
        if not self.keyword:
            self.logger.error("No keyword specified! Use: -a keyword=<keyword>")
            return

        cookies = self.get_redis_cookies()
        if not cookies:
            self.logger.warning("No valid cookies found in Redis. Trying without cookies...")

        url = f"https://www.xiaohongshu.com/search_result?keyword={urllib.parse.quote(self.keyword)}&source=web_search_result_notes"
        self.logger.info(f"Starting XHS search for keyword: '{self.keyword}' with URL: {url}")

        yield scrapy.Request(
            url,
            cookies=cookies,
            callback=self.parse_search,
            meta={'keyword': self.keyword, 'cookies': cookies}
        )

    def parse_search(self, response):
        if "login-container" in response.text or "qrcode-img" in response.text:
            self.logger.error("XHS login wall detected! Please login in Java client first.")
            return

        cards = response.css("section.note-item, div.note-item, .note-item")
        self.logger.info(f"Found {len(cards)} note cards on XHS search page")

        for card in cards:
            title = card.css(".title::text, a.title::text, .note-title::text").get()
            title = title.strip() if title else ""

            author = card.css(".author-wrapper .name::text, .nickname::text, .author .name::text").get()
            author = author.strip() if author else "小红书用户"

            like_str = card.css(".like-wrapper .count::text, .engagement .like span::text").get()
            likes = self.parse_likes(like_str)

            link = card.css("a[href*='/explore/']::attr(href), a[href*='/discovery/item/']::attr(href)").get()
            if not link:
                continue

            if not link.startswith("http"):
                note_url = f"https://www.xiaohongshu.com{link}"
            else:
                note_url = link

            note_id = self.extract_note_id(note_url)

            item = PostItem()
            item["platform"] = "xiaohongshu"
            item["post_id"] = f"xhs_{note_id}"
            item["author"] = author
            item["likes"] = likes
            item["url"] = note_url
            item["province"] = "未知"
            item["city"] = "未知"
            item["shares"] = 0
            item["comments"] = 0

            cookies = response.meta.get('cookies')
            yield scrapy.Request(
                note_url,
                cookies=cookies,
                callback=self.parse_detail,
                meta={'item': item, 'title': title}
            )

    def parse_detail(self, response):
        item = response.meta['item']
        title = response.meta['title']

        desc = response.css("#detail-desc::text, .desc::text, .note-text::text, .content::text").get()
        if desc:
            desc = desc.strip()
        else:
            desc = "".join(response.css("#detail-desc *::text, .desc *::text, .note-text *::text").getall()).strip()

        item["content"] = desc if desc else title
        item["publish_time"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        yield item

    def extract_note_id(self, url):
        if not url:
            return str(int(datetime.now().timestamp()))
        parts = url.split("?")[0].split("/")
        for part in reversed(parts):
            if re.match(r'^[a-f0-9]{24}$', part):
                return part
        return str(abs(hash(url)))

    def parse_likes(self, str_val):
        if not str_val:
            return 0
        str_val = str_val.strip()
        try:
            if "万" in str_val:
                return int(float(str_val.replace("万", "").strip()) * 10000)
            num_match = re.search(r'\d+', str_val)
            if num_match:
                return int(num_match.group())
        except Exception:
            pass
        return 0
