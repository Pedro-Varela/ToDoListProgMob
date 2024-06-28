package com.example.todolistapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerViewTasks;
    private FloatingActionButton fabAddTask;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private List<Task> taskList;
    private TaskAdapter taskAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            // Usuário não está autenticado, redirecionar para a LoginActivity
            Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(loginIntent);
            finish();
            return; // Certifique-se de sair do método para evitar exceções
        }

        recyclerViewTasks = findViewById(R.id.recyclerViewTasks);
        fabAddTask = findViewById(R.id.fabAddTask);

        recyclerViewTasks.setLayoutManager(new LinearLayoutManager(this));

        String userId = currentUser.getUid();
        databaseReference = FirebaseDatabase.getInstance().getReference("tasks").child(userId);

        taskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(taskList, databaseReference);
        recyclerViewTasks.setAdapter(taskAdapter);

        fabAddTask.setOnClickListener(v -> showAddTaskDialog());

        loadTasks();
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
                Toast.makeText(MainActivity.this, "Erro ao carregar tarefas", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Adicionar Tarefa");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, findViewById(android.R.id.content), false);
        final EditText inputTitle = viewInflated.findViewById(R.id.editTextTaskTitle);
        final EditText inputDescription = viewInflated.findViewById(R.id.editTextTaskDescription);

        builder.setView(viewInflated);

        builder.setPositiveButton("Adicionar", (dialog, which) -> {
            dialog.dismiss();
            String title = inputTitle.getText().toString();
            String description = inputDescription.getText().toString();
            if (!title.isEmpty()) {
                addTask(title, description);
            } else {
                Toast.makeText(MainActivity.this, "O título não pode estar vazio", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void addTask(String title, String description) {
        String taskId = databaseReference.push().getKey();
        if (taskId != null) {
            Task task = new Task(taskId, title, description, false);
            databaseReference.child(taskId).setValue(task).addOnCompleteListener(task1 -> {
                if (task1.isSuccessful()) {
                    Toast.makeText(MainActivity.this, "Tarefa adicionada com sucesso", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao adicionar tarefa", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(MainActivity.this, "Erro ao gerar ID da tarefa", Toast.LENGTH_SHORT).show();
        }
    }
}
