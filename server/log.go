package main

import "fmt"

type LogType struct{}

var Log LogType

func (LogType) d(format string, args ...interface{}) {
	fmt.Print("[DEBUG] ")
	fmt.Printf(format, args...)
	fmt.Println()
}

func (LogType) e(format string, args ...interface{}) {
	fmt.Print("[ERROR] ")
	fmt.Printf(format, args...)
	fmt.Println()
}

func (LogType) i(format string, args ...interface{}) {
	fmt.Print("[INFO] ")
	fmt.Printf(format, args...)
	fmt.Println()
}

func (LogType) w(format string, args ...interface{}) {
	fmt.Print("[WARNING] ")
	fmt.Printf(format, args...)
	fmt.Println()
}
