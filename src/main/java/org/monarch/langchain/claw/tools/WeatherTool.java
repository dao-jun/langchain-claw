package org.monarch.langchain.claw.tools;

import dev.langchain4j.agent.tool.Tool;

public class WeatherTool {
    @Tool("查询指定城市的天气")
    public String getWeather(String city) {
        // 模拟天气查询
        return city + " 天气：晴，25℃";
    }
}
