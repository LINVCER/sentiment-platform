# Define here the models for your scraped items
#
# See documentation in:
# https://docs.scrapy.org/en/latest/topics/items.html

import scrapy

class PostItem(scrapy.Item):
    platform = scrapy.Field()
    post_id = scrapy.Field()
    author = scrapy.Field()
    content = scrapy.Field()
    publish_time = scrapy.Field()
    likes = scrapy.Field()
    comments = scrapy.Field()
    shares = scrapy.Field()
    url = scrapy.Field()
    province = scrapy.Field()
    city = scrapy.Field()
