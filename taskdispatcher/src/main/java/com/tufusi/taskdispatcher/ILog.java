package com.tufusi.taskdispatcher;

/**
 * Created by 鼠夏目 on 2020/3/12.
 *
 * @See
 * @Description 提供日志输出
 */
public interface ILog {
    void info(String info);
    void error(String error);
}
