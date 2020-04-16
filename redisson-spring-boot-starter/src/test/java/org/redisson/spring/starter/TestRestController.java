package org.redisson.spring.starter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

/**
 * @author Nikita Koksharov
 */
@RestController
@RequestMapping("/api")
public class TestRestController {

    @GetMapping("/set")
    public void setData(HttpSession httpSession) {
        httpSession.setAttribute("testattr", "1");
    }

    @GetMapping("/get")
    public String getData(HttpSession httpSession) {
        return (String) httpSession.getAttribute("testattr");
    }
}