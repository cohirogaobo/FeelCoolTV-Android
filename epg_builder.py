import urllib.request
import xml.etree.ElementTree as ET
import json
import datetime

# 频道映射字典：前面的键名必须和你 config.json 里的 title 完全一致
TARGET_CHANNELS = {
    "CCTV-1 综合": ["CCTV-1", "CCTV1", "CCTV-1综合频道"],
    "CCTV-2 财经": ["CCTV-2", "CCTV2", "CCTV-2财经频道"],
    "CCTV-13 新闻": ["CCTV-13", "CCTV13", "CCTV-13新闻频道"],
    "江苏卫视": ["江苏卫视"]
}

# 选取你提供的直链 XML 源，解析速度最快
EPG_URL = "http://epg.51zmt.top:8000/e.xml"

def build_epg():
    try:
        # 1. 抓取庞大的 EPG 源文件
        req = urllib.request.Request(EPG_URL, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=15) as response:
            xml_data = response.read()

        root = ET.fromstring(xml_data)
        
        # 2. 匹配并提取内部频道 ID
        channel_map = {}
        for channel in root.findall('channel'):
            ch_id = channel.get('id')
            display_name = channel.find('display-name').text
            if not display_name: continue
            
            for target_title, keywords in TARGET_CHANNELS.items():
                if any(kw in display_name for kw in keywords):
                    channel_map[ch_id] = target_title
                    break

        # 3. 获取当前的东八区标准时间 (格式: YYYYMMDDHHMMSS)
        tz = datetime.timezone(datetime.timedelta(hours=8))
        now_str = datetime.datetime.now(tz).strftime("%Y%m%d%H%M%S")
        
        epg_lite = {}
        
        # 4. 筛选符合时间区间且属于目标频道的节目
        for prog in root.findall('programme'):
            ch_id = prog.get('channel')
            if ch_id not in channel_map: continue
            
            start = prog.get('start')[:14] 
            stop = prog.get('stop')[:14]
            
            if start <= now_str < stop:
                title = prog.find('title').text
                # 拼接优雅的文字格式
                epg_lite[channel_map[ch_id]] = f"正在播出：{title}"
        
        # 5. 生成极其轻量的 JSON 文件
        with open('epg_lite.json', 'w', encoding='utf-8') as f:
            json.dump(epg_lite, f, ensure_ascii=False, indent=2)
            
    except Exception as e:
        print(f"Error fetching EPG: {e}")

if __name__ == '__main__':
    build_epg()
