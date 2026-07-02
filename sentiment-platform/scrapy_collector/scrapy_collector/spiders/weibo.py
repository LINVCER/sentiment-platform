import scrapy
from urllib.parse import quote
from datetime import datetime, timedelta
import hashlib
import re
from scrapy_collector.items import PostItem

class WeiboSpider(scrapy.Spider):
    name = "weibo"
    allowed_domains = ["weibo.com"]
    
    def __init__(self, keyword=None, *args, **kwargs):
        super(WeiboSpider, self).__init__(*args, **kwargs)
        self.keyword = keyword
        self.saved_count = 0

    async def start(self):
        if not self.keyword:
            self.logger.error("No keyword specified! Use: -a keyword=<keyword>")
            return
        
        url = f"https://s.weibo.com/weibo?q={quote(self.keyword)}&timescope=custom::-1h"
        self.logger.info(f"Starting Weibo scrape for keyword: '{self.keyword}' with URL: {url}")
        
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            "Referer": "https://s.weibo.com/"
        }
        
        yield scrapy.Request(url, headers=headers, callback=self.parse, dont_filter=True)

    def parse(self, response):
        cards = response.css(".card-wrap")
        self.logger.info(f"Found {len(cards)} card-wrap elements on search page")
        
        for card in cards:
            content_selector = card.css(".content")
            if not content_selector:
                continue
            
            txt_selector = content_selector.css(".txt")
            if not txt_selector:
                continue
                
            text = "".join(txt_selector.xpath(".//text()").getall()).strip()
            if not text:
                continue
            
            author = content_selector.css(".name::text").get()
            if not author:
                author = content_selector.css("a[nick-name]::text").get() or "未知用户"
            author = author.strip()
            
            time_str = content_selector.css(".from a::text").get()
            if not time_str:
                time_str = datetime.now().strftime("%Y-%m-%d %H:%M")
            time_str = time_str.strip()
            
            publish_time = self.parse_weibo_time(time_str)
            
            # Engagement metrics
            likes = 0
            comments = 0
            shares = 0
            
            try:
                card_act = card.css(".card-act ul li")
                if len(card_act) >= 4:
                    shares_text = card_act[1].css("a::text").get()
                    comments_text = card_act[2].css("a::text").get()
                    likes_text = card_act[3].css("a::text").get()
                    
                    shares = self.parse_number(shares_text)
                    comments = self.parse_number(comments_text)
                    likes = self.parse_number(likes_text)
            except Exception:
                pass

            item = PostItem()
            item["platform"] = "weibo"
            
            # Generate stable unique ID based on content to match Java duplicate logic
            unique_str = f"weibo_{author}_{text}"
            md5_hash = hashlib.md5(unique_str.encode('utf-8')).hexdigest()
            item["post_id"] = f"wb_{md5_hash}"
            
            item["author"] = author
            item["content"] = text
            item["publish_time"] = publish_time.strftime("%Y-%m-%d %H:%M:%S")
            item["likes"] = likes
            item["comments"] = comments
            item["shares"] = shares
            item["url"] = response.url
            item["province"] = "未知"
            item["city"] = "未知"
            
            yield item

    def parse_weibo_time(self, time_str):
        try:
            if "分钟前" in time_str:
                mins = int(re.sub(r'\D+', '', time_str))
                return datetime.now() - timedelta(minutes=mins)
            elif "小时前" in time_str:
                hours = int(re.sub(r'\D+', '', time_str))
                return datetime.now() - timedelta(hours=hours)
            elif "今天" in time_str:
                parts = time_str.split()
                now = datetime.now()
                if len(parts) > 1:
                    t_parts = parts[1].split(':')
                    return now.replace(hour=int(t_parts[0]), minute=int(t_parts[1]), second=0, microsecond=0)
                return now.replace(hour=12, minute=0, second=0, microsecond=0)
            else:
                cleaned = re.sub(r'[月日]', '-', time_str).strip()
                pattern_no_year = r'^\d{2}-\d{2}\s+\d{2}:\d{2}$'
                if re.match(pattern_no_year, cleaned):
                    current_year = datetime.now().year
                    return datetime.strptime(f"{current_year}-{cleaned}", "%Y-%m-%d %H:%M")
                return datetime.strptime(cleaned, "%Y-%m-%d %H:%M")
        except Exception:
            return datetime.now()

    def parse_number(self, text):
        if not text:
            return 0
        text = text.strip()
        num_match = re.search(r'\d+', text)
        if num_match:
            return int(num_match.group())
        return 0
