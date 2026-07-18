package cn.campusmind.ai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebSearchToolTest {

    @Test
    void searchWebReturnsOnlyValidHttpResults() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.tavily.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebSearchTool tool = new WebSearchTool(builder.build(), "tvly-test", true, 5);
        server.expect(requestTo("https://api.tavily.com/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tvly-test"))
                .andRespond(withSuccess("""
                        {"results":[
                          {"title":"新疆大学招生网","url":"https://zsw.xju.edu.cn/score","content":"历年录取分数","score":0.91},
                          {"title":"无效地址","url":"javascript:alert(1)","content":"忽略","score":0.99}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var response = tool.searchWeb("新疆大学历年分数线");

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).url()).isEqualTo("https://zsw.xju.edu.cn/score");
        server.verify();
    }
}
