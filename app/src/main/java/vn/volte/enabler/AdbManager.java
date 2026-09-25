package vn.volte.enabler;

import android.content.Context;
import android.os.Build;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/** Khoá ADB riêng của app, lưu trong bộ nhớ trong của app để dùng lại sau khi ghép đôi. */
public final class AdbManager extends AbsAdbConnectionManager {

    private static AdbManager instance;

    private final PrivateKey privateKey;
    private final Certificate certificate;

    public static synchronized AdbManager get(Context context) throws Exception {
        if (instance == null) instance = new AdbManager(context.getApplicationContext());
        return instance;
    }

    private AdbManager(Context context) throws Exception {
        setApi(Build.VERSION.SDK_INT);
        setTimeout(15, TimeUnit.SECONDS);

        File keyFile = new File(context.getFilesDir(), "adb.key");
        File certFile = new File(context.getFilesDir(), "adb.crt");
        if (keyFile.exists() && certFile.exists()) {
            privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(keyFile.toPath())));
            certificate = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(Files.readAllBytes(certFile.toPath())));
            return;
        }

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        privateKey = kp.getPrivate();

        X500Name name = new X500Name("CN=VoLTE VN");
        Date from = new Date();
        Date to = new Date(from.getTime() + TimeUnit.DAYS.toMillis(365L * 30));
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(from.getTime()), from, to, name, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        certificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        Files.write(keyFile.toPath(), privateKey.getEncoded());
        Files.write(certFile.toPath(), certificate.getEncoded());
    }

    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @Override
    protected String getDeviceName() {
        return "VoLTE VN";
    }
}
