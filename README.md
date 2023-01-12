# ApproxHVPM Honk version

This repository contains a demo app that demonstrates the use of [ApproxHPVM](https://gitlab.engr.illinois.edu/llvm/hpvm-release) for approximate deep learning on Android.

To build the project, clone the repository and open it in Android studio and build.

In this repository we have all the code needed to run the demo application, with our most successful configurations of the neural network. The application is comprised of the old Matevžs demo application and our own. To use our part of the application, click "ARP activities". Then when ready click the "Start listening" button to start inference of spoken words. 

Our code is structured as follows: 

- ArpActivity -> main activity for UI and sound preprocessing.
- MicrophoneInference -> Work class that runs inference in a different thread. Also uses ApproxHPVM to approximate the network.
- HARConfidenceAdaptation -> Approximation engine that works with confidences to approximate the network.
- ArpTraceClassification -> For storing inferences in the database and sending them back to the main ativity for display o confidences.
