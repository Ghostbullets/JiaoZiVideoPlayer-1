package cn.jzvd;

import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * 数据来源
 */
public class JZDataSource {

    public static final String URL_KEY_DEFAULT = "URL_KEY_DEFAULT";//默认清晰度键

    public int currentUrlIndex;//当前清晰度视频在清晰度列表中的位置
    public LinkedHashMap urlsMap = new LinkedHashMap();//视频url键值对容器，用于切换清晰度，当size=1时隐藏清晰度切换按钮
    public String title = "";//视频标题
    public HashMap headerMap = new HashMap();//头部数据键值对容器
    public boolean looping = false;//是否循环播放视频
    public Object[] objects;//标签数组

    public JZDataSource(String url) {
        urlsMap.put(URL_KEY_DEFAULT, url);
        currentUrlIndex = 0;
    }

    public JZDataSource(String url, String title) {
        urlsMap.put(URL_KEY_DEFAULT, url);
        this.title = title;
        currentUrlIndex = 0;
    }

    public JZDataSource(Object url) {
        urlsMap.put(URL_KEY_DEFAULT, url);
        currentUrlIndex = 0;
    }

    public JZDataSource(LinkedHashMap urlsMap) {
        this.urlsMap.clear();
        this.urlsMap.putAll(urlsMap);
        currentUrlIndex = 0;
    }

    public JZDataSource(LinkedHashMap urlsMap, String title) {
        this.urlsMap.clear();
        this.urlsMap.putAll(urlsMap);
        this.title = title;
        currentUrlIndex = 0;
    }

    //返回当前url的
    public Object getCurrentUrl() {
        return getValueFromLinkedMap(currentUrlIndex);
    }

    //返回当前url的键
    public Object getCurrentKey() {
        return getKeyFromDataSource(currentUrlIndex);
    }

    //根据位置信息返回对应清晰度的url的键
    public String getKeyFromDataSource(int index) {
        int currentIndex = 0;
        for (Object key : urlsMap.keySet()) {
            if (currentIndex == index) {
                return key.toString();
            }
            currentIndex++;
        }
        return null;
    }

    //根据位置信息返回对应清晰度的url
    public Object getValueFromLinkedMap(int index) {
        int currentIndex = 0;
        for (Object key : urlsMap.keySet()) {
            if (currentIndex == index) {
                return urlsMap.get(key);
            }
            currentIndex++;
        }
        return null;
    }

    //判断是否已存在该url
    public boolean containsTheUrl(Object object) {
        if (object != null) {
            return urlsMap.containsValue(object);
        }
        return false;
    }

    //克隆该对象
    public JZDataSource cloneMe() {
        LinkedHashMap map = new LinkedHashMap();
        map.putAll(urlsMap);
        return new JZDataSource(map, title);
    }
}
