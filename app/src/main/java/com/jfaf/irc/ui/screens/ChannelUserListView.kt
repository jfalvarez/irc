package com.jfaf.irc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jfaf.irc.ui.viewmodels.MainViewModel

@Composable
fun ChannelUserListView(
    mainViewModel: MainViewModel
) {
    val userList by mainViewModel.chatScreenState.currentChannelUserList.collectAsState()
    val activeTarget by mainViewModel.chatScreenState.activeTarget.collectAsState()

    if (activeTarget?.startsWith("#") == true) {
        if (userList.isEmpty()) {
            Text(
                text = "No hay usuarios en este canal o la lista aún no se ha cargado.",
                modifier = Modifier.padding(16.dp), // << PADDING REVERTIDO
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(userList) { userName ->
                    Text(
                        text = userName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp), // << PADDING REVERTIDO
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    } else {
        Text(
            text = "Selecciona un canal para ver la lista de usuarios.",
            modifier = Modifier.padding(16.dp), // << PADDING REVERTIDO
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
