package com.mybaselink.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * StockListService
 * --------------------------------------------------------
 * python/stock/stock_list/stock_listing.json 읽기
 * --------------------------------------------------------
 */
@Service
public class StockListService {

    private final ObjectMapper mapper = new ObjectMapper();

    private File resolveJsonFile() throws IOException {
        String localPath = "D:/project/dev_boot_project/workspace/MyBaseLink/python/stock/stock_list/stock_listing.json";
        File file = new File(localPath);
        if (file.exists()) return file;
        return new ClassPathResource("data/stock_listing.json").getFile();
    }

    public List<Map<String, Object>> getStockList() throws IOException {
        File jsonFile = resolveJsonFile();
        return mapper.readValue(jsonFile, new TypeReference<List<Map<String, Object>>>() {});
    }
}
