package com.hmdp;


import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class dataWarm {

    @Autowired
    private ShopServiceImpl shopService;

    @Test
    public void test(){
        shopService.setShop2Reids(1L, 10);
    }
}
