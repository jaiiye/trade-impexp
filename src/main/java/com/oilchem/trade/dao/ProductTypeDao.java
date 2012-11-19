package com.oilchem.trade.dao;

import com.oilchem.trade.domain.ProductType;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Created with IntelliJ IDEA.
 * User: Administrator
 * Date: 12-11-5
 * Time: 下午5:03
 * To change this template use File | Settings | File Templates.
 */
public interface ProductTypeDao extends PagingAndSortingRepository<ProductType, Long> {

    ProductType findByProductType(String productType);
}
