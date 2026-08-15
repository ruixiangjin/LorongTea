package com.lorongtea.catalog;

import java.util.List;

import com.lorongtea.api.ApiResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
class CatalogController {
    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    ApiResponse<List<CatalogService.StoreView>> listStores() {
        return new ApiResponse<>(catalog.listStores());
    }

    @GetMapping("/{storeId}/menu")
    ApiResponse<List<CatalogService.MenuItemView>> listMenu(@PathVariable @Positive long storeId) {
        return new ApiResponse<>(catalog.listMenu(storeId));
    }
}
