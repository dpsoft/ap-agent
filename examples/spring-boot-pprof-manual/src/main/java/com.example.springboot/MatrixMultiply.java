package com.example.springboot;

public class MatrixMultiply extends Thread {

    static final int SIZE = 1024;
    static final int[][] A = new int[SIZE][SIZE];
    static final int[][] B = new int[SIZE][SIZE];
    public MatrixMultiply() {
        super();
    }

    @Override
    public void run() {
        int i = 0;
        for (;;) {
            matrixMultiplySlow(A, B, SIZE);
        }
    }

    //Taken from: https://github.com/krzysztofslusarski/async-profiler-demos/blob/master/first-application/src/main/java/com/example/firstapplication/CpuConsumer.java#L12
    public static int[][] matrixMultiplySlow(int[][] a, int[][] b, int size) {
        int[][] result = new int[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int sum = 0;
                for (int k = 0; k < size; k++) {
                    sum += a[i][k] * b[k][j];
                }
                result[i][j] = sum;
            }
        }
        return result;
    }
}