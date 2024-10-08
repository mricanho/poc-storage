package com.example.poc_storage;

import com.google.cloud.storage.StorageException;
import java.net.URL;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import com.google.api.gax.paging.Page;

@RestController
public class FileController {

    private final Storage storage = StorageOptions.getDefaultInstance().getService();
    private final String bucketName = "ad-poc-storage";

    // Generar URL firmada
    @GetMapping("/upload/direct")
    public String generateDSignedUrl(@RequestParam String fileName) {
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, fileName).build();
        URL signedUrl = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());
        return signedUrl.toString();
    }
    
    // Subir archivo con proxy
    @PostMapping("/upload/proxy")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.getSize() > 3 * 1024 * 1024) {
                return new ResponseEntity<>("El archivo es demasiado grande (máximo 3MB)", HttpStatus.BAD_REQUEST);
            }

            String contentType = file.getContentType();
            List<String> allowedTypes = Arrays.asList("application/pdf", "image/jpeg", "image/png");
            if (!allowedTypes.contains(contentType)) {
                return new ResponseEntity<>("Tipo de archivo no permitido", HttpStatus.BAD_REQUEST);
            }

            BlobId blobId = BlobId.of(bucketName, file.getOriginalFilename());
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();
            storage.create(blobInfo, file.getBytes());

            String fileUrl = String.format("https://storage.googleapis.com/%s/%s", bucketName, file.getOriginalFilename());
            return new ResponseEntity<>(fileUrl, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>("Error al subir el archivo", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Reemplazar archivo
    @PutMapping("/replace")
    public ResponseEntity<String> replaceFile(@RequestParam("file") MultipartFile file,
                                              @RequestParam("oldFilename") String oldFilename) {
        try {
            BlobId blobId = BlobId.of(bucketName, oldFilename);
            storage.delete(blobId);

            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, file.getOriginalFilename()).build();
            storage.create(blobInfo, file.getBytes());

            return ResponseEntity.ok("Archivo reemplazado exitosamente: " + file.getOriginalFilename());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al reemplazar archivo: " + e.getMessage());
        }
    }

    // Eliminar archivo
    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteFile(@RequestParam("filename") String filename) {
        try {
            BlobId blobId = BlobId.of(bucketName, filename);
            boolean deleted = storage.delete(blobId);

            if (deleted) {
                return ResponseEntity.ok("Archivo eliminado exitosamente: " + filename);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Archivo no encontrado: " + filename);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al eliminar archivo: " + e.getMessage());
        }
    }

    // Obtener metadatos del archivo
    @GetMapping("/files-info")
    public ResponseEntity<String> getFileInfo(@RequestParam("filename") String filename) {
        try {
            Blob blob = storage.get(BlobId.of(bucketName, filename));

            if (blob == null) {
                return new ResponseEntity<>("Archivo no encontrado", HttpStatus.NOT_FOUND);
            }

            String info = String.format("Nombre: %s, Tamaño: %d bytes, Tipo de contenido: %s",
                    blob.getName(), blob.getSize(), blob.getContentType());
            return new ResponseEntity<>(info, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>("Error al obtener la información del archivo", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Generar una URL autenticada (Signed URL)
    @GetMapping("/files/signed-url")
    public ResponseEntity<String> getSignedUrl(@RequestParam("fileName") String fileName) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, fileName).build();
            URL signedUrl = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());
            return new ResponseEntity<>(signedUrl.toString(), HttpStatus.OK);
        } catch (StorageException e) {
            e.printStackTrace();  // Esto mostrará el error exacto en los logs
            return new ResponseEntity<>("Error al generar la URL firmada: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Listar los últimos 10 archivos del bucket
    @GetMapping("/files/list")
    public ResponseEntity<?> listFiles() {
        try {
            Page<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.pageSize(10));
            
            List<String> fileList = new ArrayList<>();
            for (Blob blob : blobs.iterateAll()) {
                fileList.add(blob.getName());
            }

            if (fileList.isEmpty()) {
                return new ResponseEntity<>("No hay archivos en el bucket.", HttpStatus.OK);
            } else {
                return new ResponseEntity<>(fileList, HttpStatus.OK);
            }

        } catch (StorageException e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error al listar los archivos: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}