package com.rs.webrtc_apm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * description
 *
 * @author whs
 * @date 2024/9/24
 */
public class ShortArrayConverter {

   /**
    * 将 short 数组转换为 byte 数组
    * @param shortArray 输入的 short 数组
    * @return 转换后的 byte 数组
    */
   public static byte[] shortArrayToByteArray(short[] shortArray) {
      int shortArrayLength = shortArray.length;
      byte[] byteArray = new byte[shortArrayLength * 2];  // 每个 short 2 字节

      for (int i = 0; i < shortArrayLength; i++) {
         byteArray[i * 2] = (byte) (shortArray[i] & 0xff);         // 低8位
         byteArray[i * 2 + 1] = (byte) ((shortArray[i] >> 8) & 0xff); // 高8位
      }

      return byteArray;
   }

   // 另一种方法，使用 ByteBuffer
   public static byte[] shortArrayToByteArrayUsingByteBuffer(short[] shortArray) {
      ByteBuffer byteBuffer = ByteBuffer.allocate(shortArray.length * 2);
      byteBuffer.order(ByteOrder.LITTLE_ENDIAN);  // 可根据需求修改字节序

      for (short s : shortArray) {
         byteBuffer.putShort(s);
      }

      return byteBuffer.array();
   }

   public static void main(String[] args) {
      short[] shortArray = {256, 512, 1024, 2048};  // 示例 short 数组

      // 使用手动位操作转换
      byte[] byteArray = shortArrayToByteArray(shortArray);
      System.out.println("Byte array (manual conversion):");
      for (byte b : byteArray) {
         System.out.printf("%02X ", b);
      }

      System.out.println();

      // 使用 ByteBuffer 转换
      byte[] byteArrayBuffer = shortArrayToByteArrayUsingByteBuffer(shortArray);
      System.out.println("Byte array (using ByteBuffer):");
      for (byte b : byteArrayBuffer) {
         System.out.printf("%02X ", b);
      }
   }
}
