package com.hmdp.utils.lock;

public interface ILock {


    /**
     * timeSeconds代表
     *
     */
    public boolean tryLock(String name,long timeSeconds);


    public void unlock(String name);

}
