package com.nekodown.android;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.nekodown.android.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private final Fragment homeFragment = new HomeFragment();
    private final Fragment queueFragment = new QueueFragment();
    private final Fragment historyFragment = new HistoryFragment();
    private Fragment activeFragment = homeFragment;

    public QueueFragment getQueueFragment() {
        return (QueueFragment) queueFragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, historyFragment, "3").hide(historyFragment)
                    .add(R.id.fragment_container, queueFragment, "2").hide(queueFragment)
                    .add(R.id.fragment_container, homeFragment, "1").commit();
        }

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = homeFragment;
            } else if (itemId == R.id.nav_queue) {
                selectedFragment = queueFragment;
            } else if (itemId == R.id.nav_history) {
                selectedFragment = historyFragment;
            }
            if (selectedFragment != null && selectedFragment != activeFragment) {
                getSupportFragmentManager().beginTransaction()
                        .hide(activeFragment)
                        .show(selectedFragment)
                        .commit();
                activeFragment = selectedFragment;
            }
            return true;
        });
    }
}
