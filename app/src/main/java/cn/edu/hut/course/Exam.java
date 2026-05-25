package cn.edu.hut.course;

import java.io.Serializable;

public class Exam implements Serializable {

    public String courseName;
    public String courseCode;
    public String teacher;
    public String examDate;   // yyyy-MM-dd
    public String startTime;  // HH:mm
    public String endTime;    // HH:mm
    public String location;
    public String campus;
    public String sessionInfo; // e.g. "12周周五78节"
    public String seatNumber;
    public String ticketNumber;

    public Exam() {
    }

    public Exam(String courseName, String courseCode, String teacher,
                String examDate, String startTime, String endTime,
                String location, String campus, String sessionInfo) {
        this.courseName = courseName;
        this.courseCode = courseCode;
        this.teacher = teacher;
        this.examDate = examDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.campus = campus;
        this.sessionInfo = sessionInfo;
    }
}
