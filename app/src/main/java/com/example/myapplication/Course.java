package com.example.myapplication;

import java.io.Serializable;
import java.util.List;

public class Course implements Serializable {
    public String name = "";
    public String teacher = "";
    public String location = "";
    public int dayOfWeek = 1; // 1-7 (Mon-Sun)
    public int startSection = 1; // 1, 3, 5, 7, 9
    public int sectionSpan = 2;
    public String timeStr = "";
    public List<Integer> weeks;
    public String typeClass = ""; // classtype1, classtype2, etc.
    public boolean isExperimental = false;
    public boolean isRemark = false; // 是否属于底部备注课程 // 是否为实验课
}
