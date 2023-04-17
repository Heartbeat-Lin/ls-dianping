package com.hmdp.meidi;

import com.mysql.cj.x.protobuf.MysqlxDatatypes;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Test01 {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        String next = sc.nextLine();
        String[] sArr = next.split(" ");
        //System.out.println(sArr.length);
        int[] nums = new int[sArr.length];
        for (int i = 0; i < sArr.length; i++) {
            nums[i] = Integer.parseInt(sArr[i]);
        }
        int maxValue = 0;
        int maxIndex = 0;
        int minValue = Integer.MAX_VALUE;
        int minIndex = 0;

        for (int i = 0; i < nums.length; i++) {
            if (nums[i]>maxValue){
                maxValue = nums[i];
                maxIndex = i;
            }
            if (nums[i]<minValue){
                minValue = nums[i];
                minIndex = i;
            }
        }
        if (minIndex==0)minIndex = maxIndex;
        swap(nums,maxIndex,0);
        swap(nums,minIndex,nums.length-1);

        for (int i = 0; i < nums.length; i++) {
            System.out.print(nums[i]+" ");
        }

    }

    public static void swap(int[] arr,int a,int b){
        int tmp = arr[a];
        arr[a] = arr[b];
        arr[b] = tmp;
    }

}
