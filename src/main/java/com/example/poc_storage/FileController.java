package com.example.poc_storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class FileController {

    private final Storage storage = StorageOptions.getDefaultInstance().getService();
    private final String bucketName = "ad-poc-storage";

    @GetMapping("/upload/direct")
    public String generateSignedUrl(@RequestParam String fileName) {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, fileName).build();
        URL signedUrl = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());
        return signedUrl.toString();
    }
    
    @PostMapping("/upload/proxy")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Limitar el tamaño del archivo a 3MB
            if (file.getSize() > 3 * 1024 * 1024) {
                return new ResponseEntity<>("El archivo es demasiado grande (máximo 3MB)", HttpStatus.BAD_REQUEST);
            }

            // Validar el tipo de archivo permitido
            String contentType = file.getContentType();
            List<String> allowedTypes = Arrays.asList("application/pdf", "image/jpeg", "image/png");
            if (!allowedTypes.contains(contentType)) {
                return new ResponseEntity<>("Tipo de archivo no permitido", HttpStatus.BAD_REQUEST);
            }

            // Subir el archivo a Google Cloud Storage
            BlobId blobId = BlobId.of(bucketName, file.getOriginalFilename());
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();
            storage.create(blobInfo, file.getBytes());

            // Devolver la URL pública del archivo subido
            String fileUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, file.getOriginalFilename());
            return new ResponseEntity<>(fileUrl, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>("Error al subir el archivo", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}