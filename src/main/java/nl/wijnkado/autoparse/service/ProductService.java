package nl.wijnkado.autoparse.service;

import nl.wijnkado.autoparse.dto.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class ProductService {

    private final RestTemplate restTemplate;

    @Value("${woocommerce.base-url}")
    private String baseUrl;

    @Value("${woocommerce.consumer-key}")
    private String consumerKey;

    @Value("${woocommerce.consumer-secret}")
    private String consumerSecret;

    public ProductService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ProductDto getProductById(Long productId) {
        if (productId == null) {
            return null;
        }

        String url = baseUrl
                + "/products/" + productId
                + "?consumer_key=" + consumerKey
                + "&consumer_secret=" + consumerSecret;

        try {
            return restTemplate.getForObject(url, ProductDto.class);
        } catch (HttpStatusCodeException e) {
            // Voor nu gewoon null bij fout, je kunt dit later loggen
            System.err.println("WooCommerce product error for id " + productId + ": "
                    + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            System.err.println("Unexpected error fetching product " + productId + ": "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }
}
