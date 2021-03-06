package com.inspirecoding.supershopper.ui.shoppinglists

import android.util.Log
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.*
import com.inspirecoding.supershopper.data.Resource
import com.inspirecoding.supershopper.data.ShoppingList
import com.inspirecoding.supershopper.data.User
import com.inspirecoding.supershopper.repository.local.ShopperRepository
import com.inspirecoding.supershopper.repository.shoppinglist.ShoppingListRepository
import com.inspirecoding.supershopper.repository.user.UserRepository
import com.inspirecoding.supershopper.utils.Status
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ShoppingListsViewModel @ViewModelInject constructor(
    private val shopperRepository: ShopperRepository,
    private val userRepository: UserRepository,
    private val shoppingListRepository: ShoppingListRepository,
    @Assisted private val state: SavedStateHandle
) : ViewModel() {

    init {
        viewModelScope.launch {
            shopperRepository.getCategories()
        }
    }

    // CONST
    private val TAG = this.javaClass.simpleName
    companion object {
        const val  ARG_KEY_USER = "user"
    }

    val user = state.getLiveData<User>(ARG_KEY_USER)
    val currentUser = state.getLiveData<User>(ARG_KEY_USER)

    private val _shoppingListsFragmentsEventChannel = Channel<ShoppingListsFragmentsEvent>()
    val shoppingListsFragmentsEventChannel = _shoppingListsFragmentsEventChannel.receiveAsFlow()

    private val _shoppingLists = MutableLiveData<Resource<List<ShoppingList>>>()
    val shoppingLists : LiveData<Resource<List<ShoppingList>>> = _shoppingLists


    fun getCurrentUserShoppingListsRealTime(currentUser: User) {
        viewModelScope.launch {
            shoppingListRepository.getCurrentUserShoppingListsRealTime(currentUser, viewModelScope).collect { result ->
                Log.d(TAG, "$result")
                val shoppingLists = mutableListOf<ShoppingList>()
                when(result.status)
                {
                    Status.LOADING -> {
                        _shoppingLists.postValue(Resource.Loading(true))
                    }
                    Status.SUCCESS -> {
                        result.data?.map { _shoppingList ->
                            _shoppingList.friendsSharedWith.map { friendId ->
                                userRepository.getUserFromFirestore(friendId).collect { userResult ->
                                    when(userResult.status)
                                    {
                                        Status.LOADING -> {
                                            _shoppingLists.postValue(Resource.Loading(true))
                                        }
                                        Status.SUCCESS -> {
                                            userResult.data?.let {
                                                _shoppingList.usersSharedWith.add(it)
                                            }
                                        }
                                        Status.ERROR -> {
                                            result.message?.let {
                                                _shoppingLists.postValue(Resource.Error(it))
                                            }
                                        }
                                    }
                                }
                            }
                            shoppingLists.add(_shoppingList)
                        }

                        _shoppingLists.postValue(Resource.Success(shoppingLists))
                    }
                    Status.ERROR -> {
                        result.message?.let {
                            _shoppingLists.postValue(Resource.Error(it))
                        }
                    }
                }
            }
        }
    }





    /** Events **/
    fun onOpenSettings() {
        viewModelScope.launch {
            _shoppingListsFragmentsEventChannel.send(ShoppingListsFragmentsEvent.NavigateToSettingsFragment)
        }
    }
    fun onOpenSelectedShoppingList(shoppingList: ShoppingList) {
        viewModelScope.launch {
            _shoppingListsFragmentsEventChannel.send(ShoppingListsFragmentsEvent.OpenSelectedShoppingList(shoppingList))
        }
    }
    fun onShowErrorMessage(message: String) {
        viewModelScope.launch {
            _shoppingListsFragmentsEventChannel.send(ShoppingListsFragmentsEvent.ShowErrorMessage(message))
        }
    }






    sealed class ShoppingListsFragmentsEvent {
        object NavigateToSettingsFragment : ShoppingListsFragmentsEvent()
        data class OpenSelectedShoppingList(val shoppingList: ShoppingList) : ShoppingListsFragmentsEvent()
        data class ShowErrorMessage(val message: String) : ShoppingListsFragmentsEvent()
    }

}