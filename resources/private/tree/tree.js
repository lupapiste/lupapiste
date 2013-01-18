var selectionTree = (function () {
  "use strict";

  function defaultContentFactory(name) {
    var e = document.createElement("div");
    e.setAttribute("class", "tree-result");
    e.innerHTML = name;
    return e;
  }

  function stopProp(f) {
    return function (e) {
      var event = getEvent(e);
      event.stopPropagation();
      event.preventDefault();
      f();
      return false;
    };
  }

  function Tree(content, breadcrumbs, callback, contentFactory) {
    var self = this;

    self.data = null;
    self.content = $(content);
    self.breadcrumbs = $(breadcrumbs);
    self.callback = callback;
    self.makeTerminalElement = contentFactory || defaultContentFactory;

    self.width = content.parent().width();
    self.speed = self.width / 2; // magical, but good.
    self.crumbs = [];
    self.stack = [];

    self.goback = function () {
      if (self.stack.length > 1) {
        var d = self.stack.pop();
        var n = self.stack[self.stack.length - 1];
        $(d).animate({ "margin-left": self.width }, self.speed, function () { d.parentNode.removeChild(d); });
        $(n).animate({ "margin-left": 0 }, self.speed);
        self.crumbs.pop();
        self.breadcrumbs.html(self.crumbs.join(" / "));
      }
      return self;
    };

    self.gostart = function () {
      if (self.stack.length > 0) {
        var d = self.stack.pop();
        $(d).animate({ "margin-left": self.width }, self.speed, self.gostart2);
        self.breadcrumbs.animate({ "opacity": 0.0 }, self.speed, function () { self.breadcrumbs.html("").css("opacity", 1.0); });
        var p = d.parentNode;
        _.each(self.stack, function (n) { p.removeChild(n); });
      } else {
        self.gostart2();
      }
    };

    self.gostart2 = function () {
      self.crumbs = [];
      self.stack = [];
      self.content.empty();
      if (self.data) {
        var n = self.make(self.data);
        self.stack.push(n);
        $(n).css("margin-left", -self.width);
        self.content.append(n);
        $(n).animate({ "margin-left": 0 }, self.speed);
      }
      return self;
    };

    self.reset = function (newData) {
      if (self.stack.length > 0) {
        var d = self.stack[0];
        $(d).animate({ "margin-left": self.width }, self.speed);
      }
      self.crumbs = [];
      self.stack = [];
      self.breadcrumbs.html("");
      self.content.empty();
      if (newData) self.data = newData;
      if (self.data) {
        var n = self.make(self.data);
        self.stack.push(n);
        $(n).css("margin-left", self.width).animate({"margin-left": 0}, self.speed);
        self.content.append(n);
      }
      return self;
    };

    self.makeHandler = function (key, val, d) {
      return function (e) {
        var event = getEvent(e);
        
        event.preventDefault();
        event.stopPropagation();
        
        self.crumbs.push(key);
        self.breadcrumbs.html(self.crumbs.join(" / "));

        var terminal = typeof (val) === "string";
        var next = terminal ? self.makeTerminalElement(val) : self.make(val);
        self.stack.push(next);
        d.parentNode.appendChild(next);
        var done = (terminal && self.callback) ? self.callback.bind(self, val) : null;
        $(d).animate({ "margin-left": -self.width }, self.speed, done);
        
        return false;
      };
    };

    self.gobackEventHandler = stopProp(self.goback);
    self.gostartEventHandler = stopProp(self.gostart);


    self.make = function(t) {
      var d = document.createElement("div");
      var link;
      d.setAttribute("class", "tree-magic");
      _.each(t, function (v) { d.appendChild(self.makeLink(v[0], v[1], d)); });

      if (self.stack.length > 0) {
        var icon = document.createElement("span");
        icon.className = "font-icon icon-tree-back";
        link = document.createElement("a");
        link.className = "tree-back";
        link.innerHTML = loc("tree.back");
        link.href = "#";
        link.onclick = self.gobackEventHandler;
        link.appendChild(icon);
        d.appendChild(link);
      }

      if (self.stack.length > 1) {
        var icon = document.createElement("span");
        icon.className = "font-icon icon-tree-start";
        link = document.createElement("a");
        link.className = "tree-start";
        link.innerHTML = loc("tree.start");
        link.href = "#";
        link.onclick = self.gostartEventHandler;
        link.appendChild(icon);
        d.appendChild(link);
      }

      return d;
    };

    self.makeLink = function(key, val, d) {
      var link = document.createElement("a");
      link.innerHTML = key;
      link.href = "#";
      link.onclick = self.makeHandler(key, val, d);
      return link;
    };

  }

  return {
    create: function (content, breadcrumbs, callback, contentFactory) {
      return new Tree(content, breadcrumbs, callback, contentFactory);
    }
  };

})();
