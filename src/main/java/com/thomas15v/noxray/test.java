package com.thomas15v.noxray;

/**
 * Created by thomas15v on 12/07/16.
 */
public class test {

    public static void main(String[] args){

        int y = 15;
        int z = 15;
        int x = 15;

        int data = y << 8 | z << 4 | x;

        //y = data << 8
        //z = ?
        //x = ?

        System.out.println(y << 8);
        System.out.println(z << 4);
        System.out.println(x);
        System.out.println();



        int cy = data >> 8;
        int cz = (data ^ (cy << 8)) >> 4;
        int cx = data ^ ((cy << 8) + (cz << 4));
        System.out.println( data + " y:" + cy  + " z:" + cz + " x:" + cx);
    }

}
