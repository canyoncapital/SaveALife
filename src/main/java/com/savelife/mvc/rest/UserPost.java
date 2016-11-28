package com.savelife.mvc.rest;

import com.savelife.mvc.apis.converter.Converter;
import com.savelife.mvc.model.messaging.device.DeviceMessage;
import com.savelife.mvc.model.user.UserEntity;
import com.savelife.mvc.service.detection.DetectService;
import com.savelife.mvc.service.routing.RoutingService;
import com.savelife.mvc.service.sender.SenderService;
import com.savelife.mvc.service.user.UserRoleService;
import com.savelife.mvc.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * user rest controller receiving post requests
 */
@RestController
@RequestMapping(value = {"/rest/user"})
public class UserPost {

    private static Logger logger = Logger.getLogger(UserPost.class.getName());

    @Autowired
    SenderService senderService;

    @Autowired
    UserService userService;

    @Autowired
    DetectService detectionService;

    @Autowired
    RoutingService routingService;

    @Autowired
    UserRoleService userRoleService;

    @Autowired
    Converter<UserEntity, String> converter;

    /*
    * send path to drivers depends on ambulance position
    *
    * */
    @PostMapping(params = {"role=ambulance"})
    public Callable<ResponseEntity<Void>> postAmbulance(@RequestBody DeviceMessage deviceMessage) {

        logger.info("Received " + deviceMessage);
        return new Callable<ResponseEntity<Void>>() {
            @Override
            public ResponseEntity<Void> call() throws Exception {
                 /*find user by token*/
                try {
                    /* check the ambulance status(in race or complete)*/
                    if (!deviceMessage.isEnable()) {
                        logger.info("Make all users unable");
                        userService.setAllUsersUnable();
                        return new ResponseEntity<Void>(HttpStatus.OK);
                    }
                     /*radius of the detection*/
                    double radius = 100;

                    HashMap data = new HashMap();
                    data.put("messageBody", "Hi, would you like to rebuild your path?");
                    logger.info("Sending the massages to converted  drivers ");

                   /* getting detected users */
                    List<UserEntity> detected = detectionService.detect(
                            radius,
                            deviceMessage.getCurrentLat(),
                            deviceMessage.getCurrentLon(),
                            userService.findAllUnableDrivers());// except current

                    logger.info("Converting drivers");
                    senderService.send(converter.convert(detected, data));
                    logger.info("Sending to everyone completed");
                    logger.info("Making detected users unable");

                    /* crutch to make detected users enable (received theirs paths)  */
                    detected.forEach(v -> {
                        logger.info("Unable " + v);
                        v.setEnable(true);
                        userService.save(v);
                    });
                    logger.info("Unable users complete ");

                    return new ResponseEntity<Void>(HttpStatus.OK);
                } catch (NullPointerException e) {
                    logger.warning("Warning -> " + e);

                    return new ResponseEntity<Void>(HttpStatus.CONFLICT);
                }
            }
        };
    }

    @PostMapping(params = {"role=driver"})
    public Callable<ResponseEntity<Void>> postDriver(@RequestBody DeviceMessage deviceMessage) {
        logger.info("Received " + deviceMessage);
        return new Callable<ResponseEntity<Void>>() {
            @Override
            public ResponseEntity<Void> call() throws Exception {
                logger.info("Inside of the driver ");
                String currentToken = deviceMessage.getCurrentToken();

                if (Objects.nonNull(currentToken) && !userService.exist(currentToken)) {
                    /* save driver */
                    logger.info("Inside of the saving ");
                    UserEntity newUser = new UserEntity();
                    newUser = deviceMessage.setUserFieldsFromDeviceMessage(newUser);
                    newUser.setUserRole(userRoleService.findRoleByName("driver"));
                    logger.info("Saving user " + newUser);

                    userService.save(newUser);
                    logger.info("Saved user " + newUser);
                    return new ResponseEntity<Void>(HttpStatus.CREATED);
                } else if (Objects.nonNull(currentToken) && userService.exist(currentToken)) {
                    /*update */
                    logger.info("Inside of the updating ");
                    UserEntity userEntity = userService.findUserByToken(currentToken);
                    userEntity = deviceMessage.setUserFieldsFromDeviceMessage(userEntity);
                    logger.info("Updating user " + userEntity);

                    userService.save(userEntity);
                    logger.info("Updated user " + userEntity);
                    return new ResponseEntity<Void>(HttpStatus.OK);
                }
                return new ResponseEntity<Void>(HttpStatus.CONFLICT);
            }
        };
    }

    @PostMapping(params = {"role=person"})
    public Callable<ResponseEntity<Void>> postPerson(@RequestBody DeviceMessage deviceMessage) {
        logger.info("Received " + deviceMessage);
        return new Callable<ResponseEntity<Void>>() {
            @Override
            public ResponseEntity<Void> call() throws Exception {
                logger.info("Inside of the person ");
                if (userService.exist(deviceMessage.getCurrentToken())
                        && Objects.nonNull(deviceMessage.getCurrentToken())) {
                    /* update person*/
                    logger.info("Updating " + deviceMessage.getRole());
                    UserEntity person = userService.findUserByToken(deviceMessage.getCurrentToken());
                    logger.info("Updating user " + person);

                    person = deviceMessage.setUserFieldsFromDeviceMessage(person);
                    userService.save(person);

                    logger.info("Updated " + person);
                } else if (!userService.exist(deviceMessage.getCurrentToken())) {
                    /*save driver*/
                    logger.info("Saving person " + deviceMessage.getRole());

                    UserEntity newUser = new UserEntity();
                    newUser = deviceMessage.setUserFieldsFromDeviceMessage(newUser);
                    newUser.setUserRole(userRoleService.findRoleByName("person"));
                    userService.save(newUser);
                    logger.info("Saved user " + newUser);
                }
                if (Objects.nonNull(deviceMessage.getMessage())) {

                    logger.info("Sending message " + deviceMessage.getMessage());
                    double radius = 1000.0;//radius of the distance to notify the devices

                    /* send messages to everyone */
                    if (Objects.nonNull(deviceMessage.getCurrentLat())
                            && Objects.nonNull(deviceMessage.getCurrentLon())
                            && Objects.nonNull(deviceMessage.getCurrentToken())) {

                        HashMap data = new HashMap();
                        data.put("messageBody", "Need a help due to the " + deviceMessage.getMessage());

                        logger.info("Sending massages to everyone ");
                        /* getting detected users */
                        List<UserEntity> detected = detectionService.detect(
                                radius,
                                deviceMessage.getCurrentLat(),
                                deviceMessage.getCurrentLon(),
                                userService.findAllBeyondCurrent(deviceMessage.getCurrentToken()));

                        senderService.send(converter.convert(detected, data));
                        logger.info("Sending to everyone completed");
                    } else {
                        logger.info("massages weren't sent to everyone, currentLat and/or currentLon not found");
                    }
                }
                logger.info("postPerson method successfully finished");

                return new ResponseEntity<Void>(HttpStatus.OK);
            }
        };
    }

//    private boolean sendHelpMessageToAllContacts(long userId, String message) throws UnsupportedEncodingException {
//        List<UserEntity> userContactsList = userService.getUserContactsList(userId);
//        Converter<List<UserEntity>, List<String>> converter = (entities) -> {
//            List<String> converted = new ArrayList<>();
//
//            entities.forEach((k) -> {
//
//                Data d = new Data();
//                d.setMessageBody(message);
//
//                ServerMessage m = new ServerMessage();
//                m.setTo(k.getToken());
//                m.setData(d);
//
//                Gson gson = new Gson();
//                converted.add(gson.toJson(m));
//            });
//            return converted;
//        };
//
//        logger.info("Converting drivers");
//        List sendMessages = senderService.send(converter.convert(userContactsList));
//        if(sendMessages != null)
//            return true;
//        else
//            return false;
//    }

    @PostMapping(value = "/helpMessage/{message}")
    private String helpMessage(@PathVariable String message) throws UnsupportedEncodingException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUserPhoneNumber = auth.getName();
        UserEntity currentUser = userService.findByPhoneNumber(currentUserPhoneNumber);

        List<UserEntity> userContactsList = userService.getUserContactsList(currentUser.getIdUser());

        HashMap hashMap = new HashMap();
        hashMap.put("message", message);

        List sendMessages = senderService.send(converter.convert(
                userContactsList, hashMap));

        return  sendMessages.toString();
    }
}
