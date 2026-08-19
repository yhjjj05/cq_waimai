package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 通用接口
 */

@RestController
@RequestMapping("/admin/common")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "通用接口")
public class CommonController {

    private final AliOssUtil aliOssUtil;

    /**
     * 文件上传
     *
     * @param file 上传的文件
     * @return 文件上传结果，成功返回OSS访问URL
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        log.info("文件上传：{}", file);

        // 校验文件是否为空
        if (file.isEmpty()) {
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }

        // 校验文件大小（限制 10MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            return Result.error("文件大小不能超过2MB");
        }

        // 校验文件类型（只允许常见图片格式）
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return Result.error("文件格式不正确");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (!extension.matches(".(jpg|jpeg|png)")) {
            return Result.error("只允许上传 jpg/jpeg/png 格式的图片");
        }

        // 生成唯一文件名
        String objectName = UUID.randomUUID().toString() + extension;

        try {
            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(filePath);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
    }

}






























