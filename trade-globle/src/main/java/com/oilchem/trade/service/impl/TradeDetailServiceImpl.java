package com.oilchem.trade.service.impl;

import com.oilchem.trade.bean.ChartData;
import com.oilchem.trade.dao.*;
import com.oilchem.trade.dao.map.AbstractTradeDetailRowMapper;
import com.oilchem.trade.dao.map.ExpTradeDetailRowMapper;
import com.oilchem.trade.dao.map.ImpTradeDetailRowMapper;
import com.oilchem.trade.domain.ExpTradeDetail;
import com.oilchem.trade.domain.ImpTradeDetail;
import com.oilchem.trade.domain.Log;
import com.oilchem.trade.domain.ProductType;
import com.oilchem.trade.domain.abstrac.TradeDetail;
import com.oilchem.trade.service.CommonService;
import com.oilchem.trade.service.TradeDetailService;
import com.oilchem.trade.bean.CommonDto;
import com.oilchem.trade.bean.YearMonthDto;
import com.oilchem.trade.util.DynamicSpecifications;
import ofc4j.model.axis.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.oilchem.trade.bean.DocBean.Config.*;
import static com.oilchem.trade.bean.DocBean.ImpExpType.export_type;
import static com.oilchem.trade.bean.DocBean.ImpExpType.import_type;
import static com.oilchem.trade.bean.DocBean.TableType.detail;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.oilchem.trade.util.QueryUtils.*;
import static com.oilchem.trade.util.QueryUtils.Type.*;

/**
 * Created with IntelliJ IDEA.
 * User: Administrator
 * Date: 12-11-5
 * Time: 下午5:43
 * To change this template use File | Settings | File Templates.
 */
@Service("tradeDetailService")
public class TradeDetailServiceImpl implements TradeDetailService {

    Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    CommonService commonService;


    @Resource
    ExpTradeDetailDao expTradeDetailDao;
    @Resource
    ImpTradeDetailDao impTradeDetailDao;
    @Resource
    LogDao logDao;

    /**
     * 上传文件包
     *
     * @param file         文件
     * @param yearMonthDto
     * @return 上传后的路径
     */
    public String uploadFile(MultipartFile file, YearMonthDto yearMonthDto) {

        yearMonthDto.setTableType(detail.value());
        return commonService.uploadFile(file, upload_detailzip_dir.value(), yearMonthDto);
    }

    /**
     * 解包
     *
     * @param logId@return 解包后的文件路径
     */
    public String unPackage(Long logId) {
        Log log = logDao.findOne(logId);
        if (log != null) {
            Map<Long, Log> map = new HashMap<Long, Log>();
            map.put(log.getId(), log);
            return commonService.unpackageFile(map.entrySet().iterator().next()
                    , upload_detailzip_dir.value());
        }
        return null;
    }

    public <E extends TradeDetail, T extends AbstractTradeDetailRowMapper>
    Boolean getDetailList(final CrudRepository repository,
                          final T tradeDetailMapper,
                          final YearMonthDto yearMonthDto,
                          final String accessPath,
                          final Class detailClz,
                          List<String> sqlList) {

        Boolean isSuccess = false;
        Integer poolSize = 100;
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        for (String sqlStr : sqlList) {
            final String sql = sqlStr;
            List<E> subDetailList = null;

            Future<List<E>> detailListFuture = pool.submit(new Callable<List<E>>() {
                public List<E> call() throws Exception {

//                    return commonService.cacheListFormDB(tradeDetailMapper,
//                            yearMonthDto, accessPath, sql, detailClz);
                    return null;
                }
            });

            try {
                subDetailList = detailListFuture.get();
                repository.save(subDetailList);
                isSuccess = true;

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
        return isSuccess;
    }

    /**
     * 导入Access文件
     *
     * @param logEntry
     * @param yearMonthDto 年月
     * @return
     */
    public Boolean importAccess(Map.Entry<Long, Log> logEntry,
                                YearMonthDto yearMonthDto) {

        Boolean isSuccess = false;
        final String sql = select_access_sql.value();

        //导入查询条件表
        if (new Boolean(need_import_criteria.value())) {
            commonService.importCriteriaTab(sql, logEntry.getValue().getExtractPath());
        }

        //导入进口明细总表
        if (yearMonthDto.getImpExpType().equals(import_type.ordinal())) {

//            synchronized ("detailimp_lock".intern()) {
                Long count = impTradeDetailDao.countWithYearMonth(
                        yearMonthDto.getYear(), yearMonthDto.getMonth(), ImpTradeDetail.class);
                if (count != null && count > 0) {
                    impTradeDetailDao.delRepeatImpTradeDetail(
                            yearMonthDto.getYear(), yearMonthDto.getMonth());
                }

                commonService.importTradeDetail(
                        impTradeDetailDao,
                        new ImpTradeDetailRowMapper(),
                        yearMonthDto,
                        logEntry.getValue().getExtractPath(), sql,
                        ImpTradeDetail.class);
                isSuccess = true;
//            }
        }

        //导入出口明细表
        else if (yearMonthDto.getImpExpType().equals(export_type.ordinal())) {

//            synchronized ("detailexp_lock".intern()) {
                Long count = expTradeDetailDao.countWithYearMonth(
                        yearMonthDto.getYear(), yearMonthDto.getMonth(), ExpTradeDetail.class);
                if (count != null && count > 0) {
                    expTradeDetailDao.delRepeatImpTradeDetail(
                            yearMonthDto.getYear(), yearMonthDto.getMonth());
                }

                commonService.importTradeDetail(
                        expTradeDetailDao,
                        new ExpTradeDetailRowMapper(),
                        yearMonthDto,
                        logEntry.getValue().getExtractPath(), sql,
                        ExpTradeDetail.class);
                isSuccess = true;
//            }
        }

        return isSuccess;
    }

    /**
     * 进口明细
     *
     * @param tradeDetail  页面传来的 IxpTradeDetail包含查询条件中里面
     * @param commonDto
     * @param yearMonthDto
     * @param pageRequest  @return
     */
    public Page<ImpTradeDetail>
    findImpWithCriteria(ImpTradeDetail tradeDetail, CommonDto commonDto,
                        YearMonthDto yearMonthDto, PageRequest pageRequest) {
        final List<PropertyFilter> filterList = getdetailQueryProps(tradeDetail, commonDto);

        filterList.addAll(commonService.getYearMonthQueryProps(yearMonthDto));

        Specification<ImpTradeDetail> spec = DynamicSpecifications.<ImpTradeDetail>byPropertyFilter(filterList, ImpTradeDetail.class);
        Page<ImpTradeDetail> tradeDetailPage = impTradeDetailDao.findAll(spec, pageRequest);
        return tradeDetailPage;
    }

    /**
     * 出口明细
     *
     * @param tradeDetail  页面传来的 ExpTradeDetail，包含查询条件中里面
     * @param commonDto
     * @param yearMonthDto
     * @param pageRequest  @return
     */
    public Page<ExpTradeDetail>
    findExpWithCriteria(ExpTradeDetail tradeDetail, CommonDto commonDto,
                        YearMonthDto yearMonthDto, PageRequest pageRequest) {
        final List<PropertyFilter> filterList = getdetailQueryProps(tradeDetail, commonDto);

        filterList.addAll(commonService.getYearMonthQueryProps(yearMonthDto));

        Specification<ExpTradeDetail> spec = DynamicSpecifications.<ExpTradeDetail>byPropertyFilter(filterList,
                ExpTradeDetail.class);
        Page<ExpTradeDetail> tradeDetailPage = expTradeDetailDao.findAll(spec, pageRequest);

        return tradeDetailPage;
    }


    @Resource
    ProductTypeDao productTypeDao;

    /**
     * 获得productType列表
     *
     * @return
     */
    public List<ProductType> getProductList() {
        return (List<ProductType>) productTypeDao.findAll();
    }


    /**
     * 获得出口数据列表
     *
     * @param ids
     * @return
     */
    public List<ExpTradeDetail> getExpDetailList(List<Long> ids) {
        return ids != null ? (List<ExpTradeDetail>) expTradeDetailDao.findAll(ids) : null;
    }

    /**
     * 获得进口数据列表
     *
     * @param ids
     * @return
     */
    public List<ImpTradeDetail> getImpDetailList(List<Long> ids) {
        return ids != null ? (List<ImpTradeDetail>) impTradeDetailDao.findAll(ids) : null;
    }


//    public List<List<TradeDetail>> getChartExpDetailList(
//            List<String> names,List<Label> labels, YearMonthDto yearMonthDto){
//
//        List<List<TradeDetail>> monthDetailsList = new ArrayList<List<TradeDetail>>();
//
//        for (Label label:labels){
//            List<TradeDetail> tradeDetailList = new ArrayList<TradeDetail>();
//            for (String name : names) {
//                List<ExpTradeDetail>  tradeDetails = expTradeDetailDao.findByProductCodeAndYearMonth(name, label.getText());
//                tradeDetailList.add(new ExpTradeDetail(processChartData(name, tradeDetails)));
//            }
//            monthDetailsList.add(tradeDetailList);
//        }
//
//        return monthDetailsList;
//    }



    /**
     * 获得detailChart List
     *
     * @param codes
     * @param chartData
     * @param yearMonthDto @return     获得由月份组合而成的 list<TradeDetail>的集合
     */
    public List<ChartData<TradeDetail>> getChartDetailList(
            List<String> codes, ChartData<TradeDetail> chartData, YearMonthDto yearMonthDto) {

        List<TradeDetail> tradeDetailList = new ArrayList<TradeDetail>();
        List<ChartData<TradeDetail>> monthDetailsList = new ArrayList<ChartData<TradeDetail>>();
        Integer impExpType = yearMonthDto.getImpExpType();

        Map<String, BigDecimal> maxRangMap = chartData.getMaxRangMap();
        Map<String, BigDecimal> minRangMap = chartData.getMinRangMap();

        //遍历每个月
        for (Label label : chartData.getLabels()) {

            //遍历用户选择的名字
            for (String code : codes) {
                if (impExpType.equals(import_type.ordinal())) {
                    List<ImpTradeDetail> impTradeDetails = impTradeDetailDao.findByProductCodeAndYearMonth(code, label.getText());
                    TradeDetail tradeDetail = processChartData(code, impTradeDetails);

                    putMaxRangMap(maxRangMap,tradeDetail);
                    putMinRangMap(minRangMap,tradeDetail);

                    tradeDetailList.add(tradeDetail);
                }
                if (impExpType.equals(export_type.ordinal())) {
                    List<ExpTradeDetail> expTradeDetails = expTradeDetailDao.findByProductCodeAndYearMonth(code, label.getText());
                    TradeDetail tradeDetail = processChartData(code, expTradeDetails);

                    putMaxRangMap(maxRangMap,tradeDetail);
                    putMinRangMap(minRangMap,  tradeDetail);

                    tradeDetailList.add(tradeDetail);
                }
            }
            monthDetailsList.add(chartData.setElementList(tradeDetailList)
                    .setMaxRangMap(maxRangMap).setMinRangMap(minRangMap));
        }

        return monthDetailsList;
    }



    //最小值
    BigDecimal minAmount = BigDecimal.valueOf(0);
    BigDecimal minAmountMoney = BigDecimal.valueOf(0);
    BigDecimal minUnitPrice = BigDecimal.valueOf(0);
    private void putMinRangMap(Map<String, BigDecimal> minRangMap,  TradeDetail tradeDetail) {
        minRangMap.put("amount",tradeDetail.getAmount().compareTo(minAmount) > 0 ? minAmount : tradeDetail.getAmount());
        minRangMap.put("amount",tradeDetail.getAmountMoney().compareTo(minAmountMoney) > 0 ? minAmountMoney : tradeDetail.getAmountMoney());
        minRangMap.put("amount", tradeDetail.getUnitPrice().compareTo(minUnitPrice) > 0 ? minUnitPrice : tradeDetail.getUnitPrice());
    }

    //最大值
    BigDecimal maxAmount = BigDecimal.valueOf(0);
    BigDecimal maxAmountMoney = BigDecimal.valueOf(0);
    BigDecimal maxUnitPrice = BigDecimal.valueOf(0);
    private void putMaxRangMap(Map<String, BigDecimal> maxRangMap,  TradeDetail tradeDetail) {
        maxRangMap.put("amount",tradeDetail.getAmount().compareTo(maxAmount) < 0 ? maxAmount : tradeDetail.getAmount());
        maxRangMap.put("amountMoney", tradeDetail.getAmountMoney().compareTo(maxAmountMoney) < 0 ? maxAmountMoney : tradeDetail.getAmountMoney());
        maxRangMap.put("unitPrice", tradeDetail.getUnitPrice().compareTo(maxUnitPrice) > 0 ? maxUnitPrice : tradeDetail.getUnitPrice());
    }


    /**
     * 使用平均值构造tradeDetail供图表使用
     * @param name
     * @param tradeDetails
     * @return
     */
    private <T extends TradeDetail> TradeDetail processChartData(String name, List<T> tradeDetails) {
        BigDecimal amount = BigDecimal.valueOf(0),
                amountMoney = BigDecimal.valueOf(0),
                unitPrice = BigDecimal.valueOf(0);
        for (TradeDetail tradeDetail : tradeDetails) {
            amount = amount.add(tradeDetail.getAmount());
            amountMoney = amountMoney.add(tradeDetail.getAmountMoney());
            unitPrice = unitPrice.add(tradeDetail.getUnitPrice());
        }
        int scale = Integer.parseInt(scale_size.value());

        amount = amount.divide(BigDecimal.valueOf(tradeDetails.size())).setScale(scale, ROUND_HALF_UP);
        amountMoney = amountMoney.divide(BigDecimal.valueOf(tradeDetails.size())).setScale(scale, ROUND_HALF_UP);
        unitPrice = unitPrice.divide(BigDecimal.valueOf(tradeDetails.size())).setScale(scale, ROUND_HALF_UP);
        return new TradeDetail(name, amount, amountMoney, unitPrice);
    }


    /**
     * 获得查询属性
     *
     * @param tradeDetail
     * @param commonDto
     * @return
     */
    public List<PropertyFilter>
    getdetailQueryProps(TradeDetail tradeDetail, CommonDto commonDto) {
        List<PropertyFilter> propList = new ArrayList<PropertyFilter>();
        if (isNotBlank(tradeDetail.getCity())) {
            propList.add(new PropertyFilter("city", tradeDetail.getCity()));
        }
        if (isNotBlank(tradeDetail.getProductCode())) {
            propList.add(new PropertyFilter("productCode", tradeDetail.getProductCode(), LIKE));
        }
        if (isNotBlank(tradeDetail.getProductName())) {
            propList.add(new PropertyFilter("productName", tradeDetail.getProductName(), LIKE));
        }
        if (isNotBlank(tradeDetail.getCountry())) {
            propList.add(new PropertyFilter("country", tradeDetail.getCountry()));
        }
        if (isNotBlank(tradeDetail.getCompanyType())) {
            propList.add(new PropertyFilter("companyType", tradeDetail.getCompanyType()));
        }
        if (isNotBlank(tradeDetail.getTradeType())) {
            propList.add(new PropertyFilter("tradeType", tradeDetail.getTradeType()));
        }
        if (isNotBlank(tradeDetail.getTransportation())) {
            propList.add(new PropertyFilter("transportation", tradeDetail.getTransportation()));
        }
        if (isNotBlank(tradeDetail.getCustoms())) {
            propList.add(new PropertyFilter("customs", tradeDetail.getCustoms()));
        }
        return propList;
    }


}
