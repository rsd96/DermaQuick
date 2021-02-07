package r2.studios.skincancerdetect.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import kotlinx.android.synthetic.main.fragment_home_layout.*
import r2.studios.skincancerdetect.R

/**
 * Created by Mohamed Ramshad on 23/05/2020.
 */
class SignInFragment: Fragment() , View.OnClickListener {


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSignin.setOnClickListener(this)
    }


    override fun onClick(v: View?) {
        if (v?.id == R.id.btnSignin) {
            Navigation.findNavController(requireActivity(), R.id.fragmentContainer).navigate(
                R.id.action_signInFragment_to_permissionsFragment)
        }
    }
}