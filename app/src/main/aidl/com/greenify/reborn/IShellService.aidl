package com.greenify.reborn;

interface IShellService {
    void destroy() = 16777114;
    int runCommands(in String[] commands) = 1;
    String[] runCommandsWithOutput(in String[] commands) = 2;
}
