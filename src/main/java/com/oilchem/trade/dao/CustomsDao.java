package com.oilchem.trade.dao;

import com.oilchem.trade.domain.Customs;
import com.oilchem.trade.domain.abstrac.IdEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 海关持久类
 * Created with IntelliJ IDEA.
 * User: Administrator
 * Date: 12-11-5
 * Time: 下午4:42
 * To change this template use File | Settings | File Templates.
 */
public interface CustomsDao extends PagingAndSortingRepository<Customs, Long> {

    Customs findByCustoms(String customs);
}
