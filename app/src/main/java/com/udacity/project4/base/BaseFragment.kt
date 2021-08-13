package com.udacity.project4.base

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar

/**
 * Base Fragment to observe on the common LiveData objects
 */
abstract class BaseFragment : Fragment() {
    /**
     * Every fragment has to have an instance of a view model that extends from the BaseViewModel
     */
    abstract val saveReminderViewModel: BaseViewModel

    override fun onStart() {
        super.onStart()
        saveReminderViewModel.showErrorMessage.observe(this, {
            Toast.makeText(activity, it, Toast.LENGTH_LONG).show()
        })
        saveReminderViewModel.showToast.observe(this, {
            Toast.makeText(activity, it, Toast.LENGTH_LONG).show()
        })
        saveReminderViewModel.showSnackBar.observe(this, {
            Snackbar.make(this.requireView(), it, Snackbar.LENGTH_LONG).show()
        })
        saveReminderViewModel.showSnackBarInt.observe(this, {
            Snackbar.make(this.requireView(), getString(it), Snackbar.LENGTH_LONG).show()
        })

        saveReminderViewModel.navigationCommand.observe(this, { command ->
            when (command) {
                is NavigationCommand.To -> findNavController().navigate(command.directions)
                is NavigationCommand.Back -> findNavController().popBackStack()
                is NavigationCommand.BackTo -> findNavController().popBackStack(command.destinationId, false)
            }
        })
    }
}