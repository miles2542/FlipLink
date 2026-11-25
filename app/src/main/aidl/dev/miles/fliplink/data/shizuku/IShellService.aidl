package dev.miles.fliplink.data.shizuku;

import dev.miles.fliplink.data.shizuku.ShellResult;

interface IShellService {
    ShellResult execPm(in String[] args);
    void destroy();
}
