package com.yg.timetableservice.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yg.timetableservice.util.ExceptionPrinter;
import com.yg.timetableservice.util.LogUtil;
import com.yg.timetableservice.util.OCSUtil;
import com.yg.timetableservice.rxjava.*;
import com.yg.timetableservice.struct.ReturnResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * get turns by station
 */
@Controller
public class StationTurnsController {

    @Autowired
    private OCSUtil ocsUtil;

    @ResponseBody
    @RequestMapping("/timetable")
    public DeferredResult<String> getTurns(@RequestParam(value="cityId") final String cityId,
                                           @RequestParam(value="lineNo") final String lineNo,
                                           @RequestParam(value="direction") final int direction,
                                           @RequestParam(value="order") final int order,
                                           @RequestParam(value="show_hs_car_icon", defaultValue="0") final int showHsCarIcon,
                                           @RequestParam(value="timestamp") final int timestamp,
                                           @RequestParam(value="page", defaultValue="3") final int page) {
        long beginTimeStamp = System.currentTimeMillis();
        LogUtil.asyncDebug("request timetable cityId", cityId, "lineNo", lineNo, "dir", direction, "order", order,
                            "show_hs_car_icon", showHsCarIcon, "timestamp", timestamp, "page", page);
        final DeferredResult<String> deferredResult = new DeferredResult<>();
        try {
            String ocsKey = cityId + "#" + lineNo + "#" + direction + "#" + order;
            LogUtil.asyncDebug("ocskey:" + ocsKey);
            Observable<Object> observable = ocsUtil.asyncGetData(ocsKey, new JSONArray());
            Observable<JSONArray> turnsObservable = observable.observeOn(Schedulers.computation()).
                    map(new GetTurnsMapper(timestamp, page, cityId, lineNo, direction));
            if (showHsCarIcon == 1) {
                turnsObservable = turnsObservable.flatMap(new GetPositionFlatMapper(timestamp, ocsUtil));
            }
            Observable<ReturnResult> resultObservable = turnsObservable.map(new GetResultMapper());
            Observable<String> strResultObservable = resultObservable.flatMap(new AddOldResultFlatMapper(cityId, lineNo,
                    direction, order, timestamp));
            strResultObservable.subscribe(new ReturnResultSubscriber(deferredResult, beginTimeStamp));
        } catch (Throwable e) {
            LogUtil.error("error:" + ExceptionPrinter.getStackInfo(e));
            ReturnResult errorResult = ReturnResult.getErrorResult(e.toString());
            deferredResult.setResult("**YGKJ" + JSONObject.toJSONString(errorResult) + "YGKJ##");
        }

        return deferredResult;
    }
    @ResponseBody
    @RequestMapping("/timetable_batch")
    public DeferredResult<String> getTurnsBatch(@RequestParam(value="cityId") final String cityId,
                                                @RequestParam(value="station_keys") final String stationKeys,
                                                @RequestParam(value="show_hs_car_icon", defaultValue="0") final int showHsCarIcon,
                                                @RequestParam(value="timestamp") final int timestamp,
                                                @RequestParam(value="page", defaultValue="3") final int page) {
        long beginTimeStamp = System.currentTimeMillis();
        LogUtil.asyncDebug("request timetable_batch cityId", cityId, "station_keys", stationKeys,
                "show_hs_car_icon", showHsCarIcon, "timestamp", timestamp, "page", page);
        final DeferredResult<String> deferredResult = new DeferredResult<>();
        try {
            List<String> stations = Arrays.asList(stationKeys.split("\\|"));
            List<String> ocsKeys = new ArrayList<>();
            for (String station : stations) {
                String[] data = station.split(",");
                String ocsKey = cityId + "#" + data[0] + "#" + data[1] + "#" + data[2];
                ocsKeys.add(ocsKey);
            }
            Observable<Map<String, ?>> observable = ocsUtil.asyncGetBulkData(ocsKeys);
            Observable<Map<String, JSONArray>> turnsObservable = observable.observeOn(Schedulers.computation()).
                    map(new GetBatchTurnsMapper(timestamp, page));
            if (showHsCarIcon == 1) {
                turnsObservable = turnsObservable.flatMap(new GetBatchPositionFlatMapper(timestamp, ocsUtil));
            }
            Observable<ReturnResult> resultObservable = turnsObservable.map(new GetBatchResultMapper());
            Observable<String> strResultObservable =
                    resultObservable.flatMap(new AddOldResultBatchFlatMapper(cityId, stationKeys, timestamp));
            strResultObservable.subscribe(new ReturnResultSubscriber(deferredResult, beginTimeStamp));
        } catch (Throwable e) {
            ReturnResult errorResult = ReturnResult.getErrorResult(e.toString());
            deferredResult.setResult("**YGKJ" + JSONObject.toJSONString(errorResult) + "YGKJ##");
        }
        return deferredResult;
    }
}
