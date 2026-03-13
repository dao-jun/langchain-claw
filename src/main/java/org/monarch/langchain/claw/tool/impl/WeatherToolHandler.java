package org.monarch.langchain.claw.tool.impl;

import java.util.Map;
import org.monarch.langchain.claw.tool.ToolHandler;
import org.springframework.stereotype.Component;

@Component
public class WeatherToolHandler implements ToolHandler {

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String execute(Map<String, Object> input) {
        String city = String.valueOf(input.getOrDefault("city", "未知城市"));
        return city + " 当前天气：晴，25°C（演示数据，可替换为真实天气 API）";
    }
}
