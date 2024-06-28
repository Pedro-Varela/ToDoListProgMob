package com.example.todolistapp;

import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerViewTasks;
    private FloatingActionButton fabAddTask;
    private FloatingActionButton fabLogout;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private List<Task> taskList;
    private TaskAdapter taskAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        recyclerViewTasks = findViewById(R.id.recyclerViewTasks);
        fabAddTask = findViewById(R.id.fabAddTask);
        fabLogout = findViewById(R.id.fabLogout);

        recyclerViewTasks.setLayoutManager(new LinearLayoutManager(this));

        taskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(this, taskList);
        recyclerViewTasks.setAdapter(taskAdapter);

        fabAddTask.setOnClickListener(v -> showAddTaskDialog());
        fabLogout.setOnClickListener(v -> showLogoutDialog());

        setupFilters();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // Usuário não está autenticado, redirecionar para a LoginActivity
            Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(loginIntent);
            finish();
        } else {
            String userId = currentUser.getUid();
            databaseReference = FirebaseDatabase.getInstance().getReference("tasks").child(userId);
            taskAdapter.setDatabaseReference(databaseReference); // Passar a referência para o adaptador
            loadTasks();
        }
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.logout_confirmation))
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> {
                    mAuth.signOut();
                    Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
                    startActivity(loginIntent);
                    finish();
                })
                .setNegativeButton(getString(R.string.no), null)
                .show();
    }

    private void loadTasks() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                taskList.clear();
                for (DataSnapshot taskSnapshot : snapshot.getChildren()) {
                    Task task = taskSnapshot.getValue(Task.class);
                    if (task != null) {
                        task.setId(taskSnapshot.getKey());
                        taskList.add(task);
                    }
                }
                taskAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, getString(R.string.error_loading_tasks), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.add_task));

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, findViewById(android.R.id.content), false);
        final EditText inputTitle = viewInflated.findViewById(R.id.editTextTaskTitle);
        final EditText inputDescription = viewInflated.findViewById(R.id.editTextTaskDescription);
        final TimePicker timePickerReminder = viewInflated.findViewById(R.id.timePickerReminder);

        builder.setView(viewInflated);

        builder.setPositiveButton(getString(R.string.add), (dialog, which) -> {
            dialog.dismiss();
            String title = inputTitle.getText().toString();
            String description = inputDescription.getText().toString();
            if (!title.isEmpty()) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, timePickerReminder.getCurrentHour());
                calendar.set(Calendar.MINUTE, timePickerReminder.getCurrentMinute());
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                addTask(title, description, calendar.getTimeInMillis());
            } else {
                Toast.makeText(MainActivity.this, getString(R.string.error_empty_title), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void addTask(String title, String description, long reminderTime) {
        String taskId = databaseReference.push().getKey();
        if (taskId != null) {
            Task task = new Task(taskId, title, description, false);
            databaseReference.child(taskId).setValue(task).addOnCompleteListener(task1 -> {
                if (task1.isSuccessful()) {
                    Toast.makeText(MainActivity.this, getString(R.string.task_added_success), Toast.LENGTH_SHORT).show();
                    setTaskReminder(task, reminderTime);
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.task_add_error), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(MainActivity.this, getString(R.string.error_generating_task_id), Toast.LENGTH_SHORT).show();
        }
    }

    private void setTaskReminder(Task task, long reminderTime) {
        Intent intent = new Intent(this, ReminderBroadcastReceiver.class);
        intent.putExtra("taskId", task.getId());
        intent.putExtra("taskTitle", task.getTitle());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, task.getId().hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent);
        }
    }

    private void setupFilters() {
        Button btnViewAll = findViewById(R.id.btnViewAll);
        Button btnViewPending = findViewById(R.id.btnViewPending);
        Button btnViewCompleted = findViewById(R.id.btnViewCompleted);

        btnViewAll.setOnClickListener(v -> loadTasks());

        btnViewPending.setOnClickListener(v -> loadFilteredTasks(false));

        btnViewCompleted.setOnClickListener(v -> loadFilteredTasks(true));
    }

    private void loadFilteredTasks(boolean isCompleted) {
        databaseReference.orderByChild("completed").equalTo(isCompleted).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                taskList.clear();
                for (DataSnapshot taskSnapshot : snapshot.getChildren()) {
                    Task task = taskSnapshot.getValue(Task.class);
                    if (task != null) {
                        task.setId(taskSnapshot.getKey());
                        taskList.add(task);
                    }
                }
                taskAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, getString(R.string.error_loading_tasks), Toast.LENGTH_SHORT).show();
            }
        });
    }

    void showEditOrDeleteTaskDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.edit_or_delete_task));

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null, false);
        final EditText inputTitle = viewInflated.findViewById(R.id.editTextTaskTitle);
        final EditText inputDescription = viewInflated.findViewById(R.id.editTextTaskDescription);

        inputTitle.setText(task.getTitle());
        inputDescription.setText(task.getDescription());

        builder.setView(viewInflated);

        builder.setPositiveButton(getString(R.string.save), (dialog, which) -> {
            dialog.dismiss();
            String title = inputTitle.getText().toString();
            String description = inputDescription.getText().toString();
            if (!title.isEmpty()) {
                task.setTitle(title);
                task.setDescription(description);
                databaseReference.child(task.getId()).setValue(task);
                Toast.makeText(this, getString(R.string.task_update_success), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.error_empty_title), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(getString(R.string.delete), (dialog, which) -> {
            databaseReference.child(task.getId()).removeValue();
            taskList.remove(task);
            taskAdapter.notifyDataSetChanged();
            Toast.makeText(this, getString(R.string.task_delete_success), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        builder.setNeutralButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel());

        builder.setNeutralButton(getString(R.string.set_reminder), (dialog, which) -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.add(Calendar.MINUTE, 1); // Define o lembrete para 1 minuto no futuro
            setTaskReminder(task, calendar.getTimeInMillis());
            Toast.makeText(this, getString(R.string.reminder_set_success), Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }
}
