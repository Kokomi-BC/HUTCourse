package cn.edu.hut.course;

public class CourseTable {
    public long id;
    public String name;
    public long createTime;

    // Personal info fields (per-table)
    public String profileName = "";
    public String profileStudentId = "";
    public String profileClassName = "";
    public String profileCollege = "";

    public CourseTable() {
    }

    public CourseTable(long id, String name, long createTime) {
        this.id = id;
        this.name = name;
        this.createTime = createTime;
    }
}