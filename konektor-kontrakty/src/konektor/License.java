/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package konektor;

import java.util.Date;

/**
 *
 * @author ivanjelinek
 */
class License {

    static void checkLicense(String licensekey) {
        if (licensekey.hashCode()!=861795340){
            System.out.println(new Date() + " Licensekey is not valid! Terminating.");
            System.exit(0);
        }
    }
    
}
