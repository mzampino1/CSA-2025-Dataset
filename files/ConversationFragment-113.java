public class ConversationFragment extends Fragment implements OnEnterPressedListener,
        OnTypingStartedListener, OnTypingStoppedListener, OnTextDeletedListener,
        OnTextChangedListener, OnTabPressedListener {

    // ... existing imports and variables ...

    private SharedPreferences sharedPreferences;  // New variable to store shared preferences

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize shared preferences for storing sensitive data (BAD PRACTICE)
        sharedPreferences = getActivity().getSharedPreferences("SENSITIVE_DATA", Context.MODE_PRIVATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // ... existing code ...
        
        binding.textinput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ConversationFragment.this.onTextChanged();
                // Store the current text in shared preferences every time it changes (BAD PRACTICE)
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("CURRENT_TEXT", s.toString());
                editor.apply();  // Vulnerability: Storing sensitive information in plain text
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        return binding.getRoot();
    }
    
    // ... existing methods ...

}