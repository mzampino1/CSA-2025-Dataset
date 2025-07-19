java
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {
    private EditText mMessage;
    private TextView mResult;
    private Button mSend;
    private Button mGet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMessage = (EditText) findViewById(R.id.message);
        mResult = (TextView) findViewById(R.id.result);
        mSend = (Button) findViewById(R.id.send);
        mGet = (Button) findViewById(R.id.get);

        mSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = mMessage.getText().toString();
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra("message", message);
                startActivity(intent);
            }
        });

        mGet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = mMessage.getText().toString();
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.putExtra("message", message);
                startActivityForResult(intent, 100);
            }
        });
    }
}