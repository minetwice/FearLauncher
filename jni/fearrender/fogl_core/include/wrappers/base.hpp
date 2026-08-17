#pragma once

class BaseWrapper {
public:
    BaseWrapper() { }
    virtual ~BaseWrapper() = default;

    virtual void init() = 0;
};