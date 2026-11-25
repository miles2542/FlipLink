package dev.miles.fliplink.data.shizuku;

import dev.miles.fliplink.data.shizuku.ShellResult;

interface IShellService {
    void destroy() = 16777114;
    ShellResult execPm(in String[] args) = 1;
}
