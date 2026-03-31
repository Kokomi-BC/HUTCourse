





<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">




<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<body>
<link href="/jsxsd/assets_newL/css/common.css" rel="stylesheet"/>
<script src="/jsxsd/assets_newL/plugin/jquery-3.5.1.min.js"></script>
<script src="/jsxsd/assets_newL/plugin/layui/layui.js"></script>
<script src="/jsxsd/assets_newL/js/util.js"></script>
<script src="/jsxsd/assets_newL/js/qzTable.js"></script>
<script src="/jsxsd/assets_newL/js/qzDialog.js"></script>
<script src="/jsxsd/assets_newL/js/qzForm.js"></script>
<script src="/jsxsd/assets_newL/iconfont/icon.js"></script>
<script src="/jsxsd/assets_newL/js/qzDate.js"></script>
<script src="/jsxsd/assets_newL/js/qzImport.js" type="text/javascript"></script>
<input type="hidden" name="shouyedizhi" id="shouyedizhi" value="/jsxsd"/>
<input type="hidden" name="jbbjys" id="jbbjys" value="bg-3F7ADB"/>

<style type="text/css">
    .loading-mask {
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0,0,0,0.5);
        z-index: 9999;
        display: none;
    }
    .loading-content {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        color: #333;
        text-align: center;
        background-color: #fff;
        padding-top: 16px;
        padding: 20px;
        border-radius: 2px;
    }

    .spinner {
        margin: 20px auto;
        width: 40px;
        height: 40px;
        border: 4px solid #f3f3f3;
        border-radius: 50%;
        border-top: 4px solid #3498db;
        animation: spin 1s linear infinite;
    }

    @keyframes spin {
        0% {
            transform: rotate(0deg);
        }
        100% {
            transform: rotate(360deg);
        }
    }
</style>

</body>
</html>

<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>个人课表信息</title>
    <link href="/jsxsd/assets_newL/css/pages/PersonalCourseInfo/PersonalCourseInfo.css" rel="stylesheet" />

</head>

<body class="qz-blue qz-reLayout">
<form class="layui-form qz-search" action="/jsxsd/xskb/xskb_list.do?viweType=0" lay-filter="search" id="searchFrom">
    <div class="layui-form-item ">
        <input type="hidden" id="viweType" name="viweType" value="0">
        <input type="hidden" id="showallprint" name="showallprint" value="0">
        <input type="hidden" id="showkchprint" name="showkchprint" value="0">
        <input type="hidden" id="showkink" name="showkink" value="0">
        <input type="hidden" id="showfzmprint" name="showfzmprint" value="0">
        <input type="hidden" id="baseUrl" name="baseUrl" value="/jsxsd">
        <input type="hidden" id="xsflMapListJsonStr" name="xsflMapListJsonStr" value="讲课学时,上机学时,课程实践学时,实验学时,"/>
        <label class="layui-form-label">学年学期</label>
        <div class="layui-input-block">
            <select id="xnxq01id" name="xnxq01id" lay-filter="xnxq01id">
                
                    <option value="2026-2027-2"  >2026-2027-2</option>
                
                    <option value="2026-2027-1"  >2026-2027-1</option>
                
                    <option value="2025-2026-2"   selected="selected">2025-2026-2</option>
                
                    <option value="2025-2026-1"  >2025-2026-1</option>
                
                    <option value="2024-2025-3"  >2024-2025-3</option>
                
                    <option value="2024-2025-2"  >2024-2025-2</option>
                
                    <option value="2024-2025-1"  >2024-2025-1</option>
                
                    <option value="2023-2024-4"  >2023-2024-4</option>
                
                    <option value="2023-2024-3"  >2023-2024-3</option>
                
                    <option value="2023-2024-2"  >2023-2024-2</option>
                
                    <option value="2023-2024-1"  >2023-2024-1</option>
                
            </select>
        </div>
    </div>
    <div class="layui-form-item ">
        <label class="layui-form-label">周次</label>
        <div class="layui-input-block">
            <select id="zc" name="zc">
                <option value="">(全部)</option>
                
                <option value="1"  >第1周</option>
                
                <option value="2"  >第2周</option>
                
                <option value="3"  >第3周</option>
                
                <option value="4"  >第4周</option>
                
                <option value="5"  >第5周</option>
                
                <option value="6"  >第6周</option>
                
                <option value="7"  >第7周</option>
                
                <option value="8"  >第8周</option>
                
                <option value="9"  >第9周</option>
                
                <option value="10"  >第10周</option>
                
                <option value="11"  >第11周</option>
                
                <option value="12"  >第12周</option>
                
                <option value="13"  >第13周</option>
                
                <option value="14"  >第14周</option>
                
                <option value="15"  >第15周</option>
                
                <option value="16"  >第16周</option>
                
                <option value="17"  >第17周</option>
                
                <option value="18"  >第18周</option>
                
                <option value="19"  >第19周</option>
                
                <option value="20"  >第20周</option>
                
                <option value="21"  >第21周</option>
                
                <option value="22"  >第22周</option>
                
                <option value="23"  >第23周</option>
                
                <option value="24"  >第24周</option>
                
                <option value="25"  >第25周</option>
                
                <option value="26"  >第26周</option>
                
                <option value="27"  >第27周</option>
                
                <option value="28"  >第28周</option>
                
                <option value="29"  >第29周</option>
                
                <option value="30"  >第30周</option>
                
            </select>
        </div>
    </div>
    <div class="layui-form-item ">
        <label class="layui-form-label">时间模式</label>
        <div class="layui-input-block">
            <select id="kbjcmsid" name="kbjcmsid">
                
                    <option value="94D51EECEBF4F9B4E053474110AC8060" selected="selected">默认节次模式</option>
                
            </select>
        </div>
    </div>
    <div class="qz-search-btn">
        <button class="layui-btn" type="submit" lay-submit lay-filter="formDemo">
            <iconpark-icon name="chazhao2x"></iconpark-icon>查询
        </button>
        <button plain type="reset" class="layui-btn">
            <iconpark-icon name="zhongzhi"></iconpark-icon>重置
        </button>
        <button plain type="button" expland class="layui-btn">
            <iconpark-icon name="xuanzekuangshangla"></iconpark-icon>展开
        </button>
    </div>
</form>
<div class="qz-content qz-flex-col">
    <div class="qz-contentHead qz-flex-row">
        <form class="layui-form " action="" lay-filter="search1">
            <div class="layui-form-item layui-form-text">
                <div class="layui-input-block">
                    <input type="checkbox" name="1" value="1"  title=" 放大" lay-filter="checkboxfilter">
                    <input type="checkbox" name="2" value="2"  title="显示课程编号" lay-filter="checkboxfilter">
                    <input type="checkbox" name="4" value="4"  title="显示网课群号及链接" lay-filter="checkboxfilter">
                    <input type="checkbox" name="5" value="5"  title="显示分组名" lay-filter="checkboxfilter">

                    <span id="tsMsg"  style="color: red"></span>
                </div>
            </div>
        </form>
        <div class="qz-contentHead-right qz-flex-row">
            <!-- 课程颜色图例 -->
            <!-- classtype1为课程颜色共有七种以此类推 -->
            <ul class="legend-lists qz-flex-row">
                
                    <li class="legend-item qz-flex-row">
                        <div class="sqaure-icon classtype1" ></div>
                        <div class="legend-text">必修</div>
                    </li>
                
                    <li class="legend-item qz-flex-row">
                        <div class="sqaure-icon classtype2" ></div>
                        <div class="legend-text">限选</div>
                    </li>
                
                    <li class="legend-item qz-flex-row">
                        <div class="sqaure-icon classtype3" ></div>
                        <div class="legend-text">任选</div>
                    </li>
                
                    <li class="legend-item qz-flex-row">
                        <div class="sqaure-icon classtype4" ></div>
                        <div class="legend-text">公选</div>
                    </li>
                
                    <li class="legend-item qz-flex-row">
                        <div class="sqaure-icon classtype5" ></div>
                        <div class="legend-text">选修</div>
                    </li>
                
                    <li class="legend-item qz-flex-row">
                        <div class="sqaure-icon classtype9" ></div>
                        <div class="legend-text">其它</div>
                    </li>
                
            </ul>
            <button plain type="button" class="layui-btn" lay-on="loadKb_Excl"  >
                <iconpark-icon name="upload2"></iconpark-icon>导出
            </button>
        </div>
    </div>
    <div class="qz-table-box">
        <table class="qz-weeklyTable" border="1">
            <!-- qz-currentWeek 标识当前星期 -->
            <thead class="qz-weeklyTable-thead">
            <tr class="qz-weeklyTable-tr">
                <th class="qz-weeklyTable-th qz-weeklyTable-label qz-ellipse" width="122px">
                    周次
                </th>
                
                <th class="qz-weeklyTable-th qz-ellipse " width="13%">
                    星期一
                </th>
                <th class="qz-weeklyTable-th qz-ellipse qz-currentWeek" width="13%">
                    星期二
                </th>
                <th class="qz-weeklyTable-th qz-ellipse " width="13%">
                    星期三
                </th>
                <th class="qz-weeklyTable-th qz-ellipse " width="13%">
                    星期四
                </th>
                <th class="qz-weeklyTable-th qz-ellipse " width="13%">
                    星期五
                </th>
                <th class="qz-weeklyTable-th qz-ellipse " width="13%">
                    星期六
                </th>
                
                    <th class="qz-weeklyTable-th qz-ellipse " width="13%">
                        星期日
                    </th>
                
            </tr>
            </thead>
            <!--主体部分
               "qz-weeklyTable-label"表示第一列的样式
              "qz-showFullInfo"类切换判断是否展示所有课程信息 在tbody的类上加可看到效果 qz-hasCourse-fullinfo
               "isThreeCourse"标识非合并单元格非放大下有三门及三门以上课程，只有第一门显示全，其他不显示全
              "isThreeCourseOpen"标识放大的情况
              "qz-showkcbh"类表示勾选显示课程编号
              "qz-showlink"类表示勾选显示链接
              "qz-hasCourse"类标识有课程
               "qz-default"标识不显示内容
                "qz-showkcbhInside"类标识显示提示框中的课程号
              "qz-showWeiCode"类标识显示提示框中的二维码/群号网址
              "qz-hasCourse-1"类标识星期一有课的样式以此类推
              "hasMoreCourse"标识有多门课程的情况
               一天内连续两大节相同的课需要通过rowspan设置行合并
               并且需要加上"qz-mixrow"标识设置了rowspan的单元格,
               注意：合并行需要动态设置高度，且分为两种（单行高度*行，示例合并两行），1.信息简写：(152*2)+4，2.信息全部显示：(192*2)+4
            -->
            <tbody class="qz-weeklyTable-thbody " >
            
                <tr class="qz-weeklyTable-tr">
                    <td class="qz-weeklyTable-td qz-weeklyTable-label " width="122px"  name="timeTd" height="156" >
                        <div class="td-cell qz-ellipse qz-flex-col">
                            <div class="index-title">
                                    第一大节
                            </div>
                                
                                <div class="index-detailtext">
                                    (01、02小节)
                                </div>
                            

                            
                                <div class="index-detailtext qz-flex-row">
                                    <iconpark-icon name="time"></iconpark-icon>
                                    <span> 08:00~09:40</span>
                                </div>
                            

                        </div>
                    </td>
                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-5" >
                                                <div class="qz-hasCourse-title qz-ellipse">Python数据处理</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:蒋鸿副教授;时间:2,4,6,8,10周[1-2节];地点:公共楼(公共108)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:08152600</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2303-2304]班;总人数:80;考核方式:考试;
                                                                          总学时:10
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">Python数据处理</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：08152600
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2303-2304]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：80
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：蒋鸿副教授
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：2,4,6,8,10周[1-2节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：10</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：公共楼(公共108)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262007995" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-5" >
                                                <div class="qz-hasCourse-title qz-ellipse">软件项目管理</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:何频捷讲师;时间:1-11周[1-2节];地点:公共楼(公共108)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:08152570</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2303-2304]班;总人数:80;考核方式:考试;
                                                                          总学时:22
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">软件项目管理</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：08152570
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2303-2304]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：80
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：何频捷讲师
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：1-11周[1-2节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：22</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：公共楼(公共108)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262008003" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                </tr>
            
                <tr class="qz-weeklyTable-tr">
                    <td class="qz-weeklyTable-td qz-weeklyTable-label " width="122px"  name="timeTd" height="156" >
                        <div class="td-cell qz-ellipse qz-flex-col">
                            <div class="index-title">
                                    第二大节
                            </div>
                                
                                <div class="index-detailtext">
                                    (03、04小节)
                                </div>
                            

                            
                                <div class="index-detailtext qz-flex-row">
                                    <iconpark-icon name="time"></iconpark-icon>
                                    <span> 10:00~11:40</span>
                                </div>
                            

                        </div>
                    </td>
                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-5" >
                                                <div class="qz-hasCourse-title qz-ellipse">软件项目管理</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:何频捷讲师;时间:1,3,5,7,9周[3-4节];地点:公共楼(公共108)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:08152570</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2303-2304]班;总人数:80;考核方式:考试;
                                                                          总学时:10
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">软件项目管理</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：08152570
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2303-2304]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：80
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：何频捷讲师
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：1,3,5,7,9周[3-4节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：10</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：公共楼(公共108)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262008003" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-1" >
                                                <div class="qz-hasCourse-title qz-ellipse">形势与政策</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:侯晓红副教授;时间:11-12周[3-4节];地点:公共楼(公共205)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:29110162</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2301-2303]班;总人数:118;考核方式:考试;
                                                                          总学时:4
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">形势与政策</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：29110162
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2301-2303]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：118
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：侯晓红副教授
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：11-12周[3-4节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：4</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：公共楼(公共205)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262007981" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-1" >
                                                <div class="qz-hasCourse-title qz-ellipse">大数据系统及应用</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:沈浩讲师;时间:1-6周[3-4节];地点:计算机楼(计通楼206)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:08121810</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2303-2304]班;总人数:80;考核方式:考试;
                                                                          总学时:12
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">大数据系统及应用</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：08121810
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2303-2304]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：80
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：沈浩讲师
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：1-6周[3-4节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：12</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：计算机楼(计通楼206)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262007991" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                </tr>
            
                <tr class="qz-weeklyTable-tr">
                    <td class="qz-weeklyTable-td qz-weeklyTable-label " width="122px"  name="timeTd" height="156" >
                        <div class="td-cell qz-ellipse qz-flex-col">
                            <div class="index-title">
                                    第三大节
                            </div>
                                
                                <div class="index-detailtext">
                                    (05、06小节)
                                </div>
                            

                            
                                <div class="index-detailtext qz-flex-row">
                                    <iconpark-icon name="time"></iconpark-icon>
                                    <span> 14:00~15:40</span>
                                </div>
                            

                        </div>
                    </td>
                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-1" >
                                                <div class="qz-hasCourse-title qz-ellipse">编译原理</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:郭青松讲师;时间:1-7周[5-6节];地点:公共楼(公共519)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:08120061</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2303-2304]班;总人数:80;考核方式:考试;
                                                                          总学时:14
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">编译原理</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：08120061
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2303-2304]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：80
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：郭青松讲师
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：1-7周[5-6节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：14</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：公共楼(公共519)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262007999" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-1" >
                                                <div class="qz-hasCourse-title qz-ellipse">编译原理</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:郭青松讲师;时间:2-13周[5-6节];地点:公共楼(公共409)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:08120061</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2303-2304]班;总人数:80;考核方式:考试;
                                                                          总学时:24
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">编译原理</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：08120061
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2303-2304]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：80
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：郭青松讲师
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：2-13周[5-6节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：24</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：公共楼(公共409)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262007999" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                </tr>
            
                <tr class="qz-weeklyTable-tr">
                    <td class="qz-weeklyTable-td qz-weeklyTable-label " width="122px"  name="timeTd" height="156" >
                        <div class="td-cell qz-ellipse qz-flex-col">
                            <div class="index-title">
                                    第四大节
                            </div>
                                
                                <div class="index-detailtext">
                                    (07、08小节)
                                </div>
                            

                            
                                <div class="index-detailtext qz-flex-row">
                                    <iconpark-icon name="time"></iconpark-icon>
                                    <span> 16:00~17:40</span>
                                </div>
                            

                        </div>
                    </td>
                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-1" >
                                                <div class="qz-hasCourse-title qz-ellipse">大数据系统及应用</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:沈浩讲师;时间:1-12周[7-8节];地点:计算机楼(计通楼206)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:08121810</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2303-2304]班;总人数:80;考核方式:考试;
                                                                          总学时:24
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">大数据系统及应用</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：08121810
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2303-2304]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：80
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：沈浩讲师
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：1-12周[7-8节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：24</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：计算机楼(计通楼206)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262007991" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-5" >
                                                <div class="qz-hasCourse-title qz-ellipse">Python数据处理</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:蒋鸿副教授;时间:1-11周[7-8节];地点:公共楼(公共108)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:08152600</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2303-2304]班;总人数:80;考核方式:考试;
                                                                          总学时:22
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">Python数据处理</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：08152600
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2303-2304]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：80
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：蒋鸿副教授
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：1-11周[7-8节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：22</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：公共楼(公共108)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262007995" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-1" >
                                                <div class="qz-hasCourse-title qz-ellipse">就业指导</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:刘承宗讲师;时间:4-7周[7-8节];地点:公共楼(公共510)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:51110010</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2301-2303]班;总人数:118;考核方式:考查;
                                                                          总学时:8
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">就业指导</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：51110010
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2301-2303]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：118
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考查
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：刘承宗讲师
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：4-7周[7-8节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：8</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：公共楼(公共510)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262007977" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="1"    colSize="1" name="kbdataUl">
                                        
                                            <li class="courselists-item qz-hasCourse-1" >
                                                <div class="qz-hasCourse-title qz-ellipse">编译原理</div>
                                                <p class="qz-hasCourse-detaillists ">
                                                    <span class="qz-hasCourse-detailitem qz-hasCourse-abbrinfo">
                                                             老师:郭青松讲师;时间:1周[7-8节];地点:公共楼(公共122)
                                                    </span>
                                                    <span name="kchSpan" class="qz-default">;课程号:08120061</span>
                                                    <span name="dealeSpan" class="qz-hasCourse-fullinfo">班级:计算机[2303-2304]班;总人数:80;考核方式:考试;
                                                                          总学时:2
                                                     </span>
                                                    <span name="linkSpan" class="qz-default "></span>
                                                    <span name="fzmSpan" class="qz-default ">;分组名:</span>
                                                </p>
                                            </li>
                                        
                                    </ul>
                                    
                                        <div class="qz-tooltip">
                                            <div class="qz-tooltipContent">
                                                <ul class="qz-toolitiplists">
                                                    
                                                        <li class="qz-toolitiplists">
                                                            <div class="qz-tooltipContent-title qz-ellipse">编译原理</div>
                                                            <div class="qz-tooltipContent-detaillists">
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="kchDiv">
                                                                    课程号：08120061
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    班级：计算机[2303-2304]班
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    总人数：80
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    考核方式：考试
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    老师：郭青松讲师
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span>时间：1周[7-8节]</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    <span> 总学时：2</span>
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem">
                                                                    地点：公共楼(公共122)
                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="linkDiv">
                                                                    <p>网课群号：</p>
                                                                    <p>网课链接：</p>
                                                                    <div class="qz-tooltipContent-detailitem qz-flex-row weiCodeItem">
                                                                        <div class="weiCodeBorder">
                                                                            <img src="/jsxsd/jsxx/ewmck?id=202520262007999" class="weiCode" />
                                                                        </div>
                                                                    </div>

                                                                </div>
                                                                <div class="qz-tooltipContent-detailitem qz-default" name="fzmDiv">
                                                                    分组名：
                                                                </div>

                                                            </div>
                                                        </li>
                                                    
                                                </ul>
                                            </div>
                                        </div>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                </tr>
            
                <tr class="qz-weeklyTable-tr">
                    <td class="qz-weeklyTable-td qz-weeklyTable-label " width="122px"  name="timeTd" height="156" >
                        <div class="td-cell qz-ellipse qz-flex-col">
                            <div class="index-title">
                                    第五大节
                            </div>
                                
                                <div class="index-detailtext">
                                    (09、10小节)
                                </div>
                            

                            
                                <div class="index-detailtext qz-flex-row">
                                    <iconpark-icon name="time"></iconpark-icon>
                                    <span> 19:00~20:40</span>
                                </div>
                            

                        </div>
                    </td>
                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                        
                            <td rowspan="1" name="kbDataTd"  class="qz-weeklyTable-td  qz-hasCourse "  width="13%" colSize="1"
                                height="156" >
                                <div class="td-cell qz-flex-col ">
                                    <ul class="courselists qz-flex-col   " kbdataSize="0"    colSize="1" name="kbdataUl">
                                        
                                    </ul>
                                    

                                </div>
                            </td>
                        

                    
                </tr>
            
            </tbody>
            <!-- 尾部  需要注意最后一行的第二个单元格需要设置colspan="7"以此合并7个单元格-->
            <tfoot class="qz-weeklyTable-tfoot" >
            <tr class="qz-weeklyTable-tr">
                <td class="qz-weeklyTable-td qz-weeklyTable-label" width="122px">
                    <div class="td-cell qz-ellipse qz-flex-col">
                        <div class="index-title">备注</div>
                    </div>
                </td>
                <td class="qz-weeklyTable-td qz-weeklyTable-detailtext" colspan="7" width="91%">
                    
                    <div class="td-cell">
                        生产实习 郭青松 15-18周;软件项目管理 何频捷 1-4周;Python数据处理 蒋鸿 1-8周;大学生劳动教育  1周;大学生劳动教育  1-18周;计算机组成原理  1周;大数据系统及应用 沈浩 1-6周;Linux操作系统实验 文志强 1-12周;大数据系统及应用课程设计 沈浩 14周;
                    </div>
                    
                </td>
            </tr>
            </tfoot>
        </table>
    </div>
</div>
</body>

</html>
<script type="module" src="/jsxsd/assets_newL/js/common.js"></script>
<script>
    let searchObj = {};
    let checkboxObj = {'showall':'0','showkch':'0','shownetlink':'0','showfzm':'0'};
    if('11535' == '10532'){
        checkboxObj = {'showall':'0','showkch':'0','shownetlink':'1','showfzm':'1'};
    }
    var util = layui.util
    layui.use(['form', 'table', 'element'], function() {
        var table = layui.table;
        var form = layui.form;
        var element = layui.element
        //监听提交
        form.on('submit(formDemo)', function(data) {
            searchObj = data.field;
            return true;
        });

        //查询条件进行联动
        form.on('select(xnxq01id)',function(data){
            selectreach(data.value);
        });

        //界面初始化联动
        selectreach($("#xnxq01id").val());

        function selectreach(xnxq01id){
            $.ajax({
                type : "GET",
                url : "/jsxsd/xskb/jxzlzc_xnxq_ajax",
                data : "&xnxq01id="+xnxq01id,
                dataType: "json",
                success : function(msg) {
                    AddOptions(msg,"zc");

                    //渲染select
                    form.render("select");
                }
            });
        }
        function AddOptions(data,id){
            var obj = $("#"+id);
            obj.empty();//清除
            obj.append("<option value=''>(全部)</option>");
            debugger
            var kszc = 1;
            var jszc = 30
            if(data != null && data.length > 0){
                kszc = data[0].qszc;
                jszc = data[0].jszc;
            }
            for (var i = kszc ;i <= jszc; i++) {
                var opt = new Option("第"+i+"周",i);
                var zc=""
                if(zc==i){
                    opt.selected=true
                }
                obj.append(opt);
            }
        }

        //  监听复选框切换事件
        form.on('checkbox(checkboxfilter)', function(data) {
            if(data.value=='1'){
                if(checkboxObj.showall=='0'){
                    $("#tbody_table").addClass("qz-showFullInfo");
                    checkboxObj.showall='1';
                    $("span[name='dealeSpan']").each(function () {
                        $(this).removeClass("qz-hasCourse-fullinfo")
                    });
                    $("td[name='timeTd']").each(function () {
                        $(this).attr("height",192);
                    })

                   $("td[name='kbDataTd']").each(function () {
                       $(this).attr("height",parseFloat($(this).attr("colSize")*192)+parseFloat($(this).attr("colSize")));
                    })

                    $("ul[name='kbdataUl']").each(function () {
                        $(this).removeClass("isThreeCourse")
                        if($(this).attr("kbdatasize")>=3){
                            $(this).addClass("isThreeCourseOpen")
                        }
                        if($(this).attr("colSize")>1){
                            $(this).removeClass("isThreeCourse")
                            $(this).removeClass("isThreeCourseOpen")
                        }
                    })

                }else{
                    checkboxObj.showall='0';
                    $("#tbody_table").addClass("qz-showFullInfo");
                    $("span[name='dealeSpan']").each(function () {
                        $(this).addClass("qz-hasCourse-fullinfo")
                    });
                    $("ul[name='kbdataUl']").each(function () {
                        $(this).removeClass("isThreeCourseOpen")
                        if($(this).attr("kbdatasize")>=3){
                            $(this).addClass("isThreeCourse")
                        }
                        if($(this).attr("colSize")>1){
                            $(this).removeClass("isThreeCourse")
                            $(this).removeClass("isThreeCourseOpen")
                        }
                    })
                    $("td[name='timeTd']").each(function () {
                        $(this).attr("height",156);
                    })

                    $("td[name='kbDataTd']").each(function () {
                        $(this).attr("height",parseFloat($(this).attr("colSize")*155)+parseFloat($(this).attr("colSize")));
                    })
                }
            }else if(data.value=='2'){
                if(checkboxObj.showkch=='0'){
                    checkboxObj.showkch='1';
                        $("span[name='kchSpan']").each(function () {
                            $(this).addClass("qz-showkcbh")
                        });

                    $("div[name='kchDiv']").each(function () {
                        $(this).addClass("qz-showkcbhInside")
                    });
                    //qz-showkcbh
                    $("td[name='kbDataTd']").each(function () {
                        if(checkboxObj.showall=='1'){
                            $(this).attr("height",parseFloat($(this).attr("colSize")*192)+parseFloat($(this).attr("colSize"))*26);
                        }else{
                            $(this).attr("height",parseFloat($(this).attr("colSize")*155)+parseFloat($(this).attr("colSize"))*26);
                        }
                    })
                }else{
                    checkboxObj.showkch='0';
                        $("span[name='kchSpan']").each(function () {
                            $(this).removeClass("qz-showkcbh")
                        });
                    $("div[name='kchDiv']").each(function () {
                        $(this).removeClass("qz-showkcbhInside")
                    });
                    $("td[name='kbDataTd']").each(function () {
                        if(checkboxObj.showall=='1'){
                            $(this).attr("height",parseFloat($(this).attr("colSize")*192)+parseFloat($(this).attr("colSize")));
                        }else{
                            $(this).attr("height",parseFloat($(this).attr("colSize")*155)+parseFloat($(this).attr("colSize")));
                        }
                    })
                }
            }else if(data.value=='4'){
                if(checkboxObj.shownetlink=='0'){
                    checkboxObj.shownetlink='1';
                        $("span[name='linkSpan']").each(function () {
                            $(this).addClass("qz-showlink")
                        });
                    $("div[name='linkDiv']").each(function () {
                        $(this).addClass("qz-showWeiCode")
                    });
                }else {
                    checkboxObj.shownetlink='0';
                        $("span[name='linkSpan']").each(function () {
                            $(this).removeClass("qz-showlink")
                        });
                    $("div[name='linkDiv']").each(function () {
                        $(this).removeClass("qz-showWeiCode")
                    });
                }
            }else if(data.value=='5'){
                if(checkboxObj.showfzm=='0'){
                    checkboxObj.showfzm='1';
                    $("span[name='fzmSpan']").each(function () {
                        $(this).addClass("qz-showkcbh")
                    });

                    $("div[name='fzmDiv']").each(function () {
                        $(this).addClass("qz-showkcbhInside")
                    });
                    //qz-showkcbh
                    $("td[name='kbDataTd']").each(function () {
                        if(checkboxObj.showall=='1'){
                            $(this).attr("height",parseFloat($(this).attr("colSize")*192)+parseFloat($(this).attr("colSize"))*26);
                        }else{
                            $(this).attr("height",parseFloat($(this).attr("colSize")*155)+parseFloat($(this).attr("colSize"))*26);
                        }
                    })
                }else{
                    checkboxObj.showfzm='0';
                    $("span[name='fzmSpan']").each(function () {
                        $(this).removeClass("qz-showkcbh")
                    });
                    $("div[name='fzmDiv']").each(function () {
                        $(this).removeClass("qz-showkcbhInside")
                    });
                    $("td[name='kbDataTd']").each(function () {
                        if(checkboxObj.showall=='1'){
                            $(this).attr("height",parseFloat($(this).attr("colSize")*192)+parseFloat($(this).attr("colSize")));
                        }else{
                            $(this).attr("height",parseFloat($(this).attr("colSize")*155)+parseFloat($(this).attr("colSize")));
                        }
                    })
                }
            }
        })
        util.on('lay-on', {
            loadKb_Excl: function () {
                

                var oldAction = document.getElementById("searchFrom").action;
                var oldtarget = document.getElementById("searchFrom").target;
                document.getElementById("searchFrom").target='hideFrame';
                document.getElementById("showallprint").value=checkboxObj.showall;
                document.getElementById("showkchprint").value=checkboxObj.showkch;
                document.getElementById("showkink").value=checkboxObj.shownetlink;
                document.getElementById("showfzmprint").value=checkboxObj.showfzm;
                document.getElementById("searchFrom").action = "/jsxsd/xskb/xskb_print.do";
                document.getElementById("searchFrom").submit();
                document.getElementById("searchFrom").action = oldAction;
                document.getElementById("searchFrom").target = oldtarget;
            },
        })
    });


</script>
