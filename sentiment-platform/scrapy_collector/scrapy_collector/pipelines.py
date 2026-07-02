import mysql.connector
from itemadapter import ItemAdapter
import logging

class MySQLPipeline:
    def __init__(self, host, port, user, password, database):
        self.host = host
        self.port = port
        self.user = user
        self.password = password
        self.database = database
        self.logger = logging.getLogger(self.__class__.__name__)

    @classmethod
    def from_crawler(cls, crawler):
        return cls(
            host=crawler.settings.get('MYSQL_HOST', 'localhost'),
            port=crawler.settings.get('MYSQL_PORT', 3306),
            user=crawler.settings.get('MYSQL_USER', 'root'),
            password=crawler.settings.get('MYSQL_PASSWORD', '123456'),
            database=crawler.settings.get('MYSQL_DATABASE', 'sentiment_db'),
        )

    def open_spider(self, spider):
        self.conn = mysql.connector.connect(
            host=self.host,
            port=self.port,
            user=self.user,
            password=self.password,
            database=self.database,
            charset='utf8mb4'
        )
        self.cursor = self.conn.cursor()

    def close_spider(self, spider):
        if hasattr(self, 'cursor') and self.cursor:
            self.cursor.close()
        if hasattr(self, 'conn') and self.conn:
            self.conn.close()

    def process_item(self, item, spider):
        adapter = ItemAdapter(item)
        
        data = {
            'platform': adapter.get('platform'),
            'post_id': adapter.get('post_id'),
            'author': adapter.get('author'),
            'content': adapter.get('content', ''),
            'publish_time': adapter.get('publish_time'),
            'likes': adapter.get('likes', 0),
            'comments': adapter.get('comments', 0),
            'shares': adapter.get('shares', 0),
            'url': adapter.get('url'),
            'province': adapter.get('province'),
            'city': adapter.get('city'),
            'analyze_status': 0  # 0 = pending
        }
        
        columns = ", ".join(data.keys())
        placeholders = ", ".join(["%s"] * len(data))
        sql = f"INSERT IGNORE INTO posts ({columns}) VALUES ({placeholders})"
        
        try:
            self.cursor.execute(sql, tuple(data.values()))
            self.conn.commit()
            if self.cursor.rowcount > 0:
                spider.logger.info(f"Saved new post: {data['post_id']} to MySQL")
                spider.saved_count = getattr(spider, 'saved_count', 0) + 1
        except mysql.connector.Error as err:
            self.logger.error(f"Failed to insert item to MySQL: {err}")
            self.conn.rollback()
            
        return item
