var selectionTree = (function() {

  function Tree(data, content, breadcrumbs) {
    var self = this;
    
    self.content = $(content);
    self.breadcrumbs = $(breadcrumbs);
    self.data = data;
        
    self.goback = function() {
      if (self.stack.length > 1) {
        var d = self.stack.pop();
        var n = self.stack[self.stack.length - 1];
        $(d).animate({"margin-left": 400}, speed, function() { n.parentNode.removeChild(d); });
        $(n).animate({"margin-left": 0}, speed);  
        self.crumbs.pop();
        self.breadcrumbs.html(self.crumbs.join(" / "));
      }
      return self;
    };
    
    self.make = function(t) {
      var d = document.createElement("div");
      d.setAttribute("class", "tree-magic");
      for (var key in t) d.appendChild( self.makeLink(key, t[key], d) );
      return d;
    };

    self.makeLink  = function(key, val, d) {
      var link = document.createElement("a");
      link.innerHTML = key;
      link.href = "#";
      link.onclick = self.makeHandler(key, val, d);
      return link;
    };
    
    self.makeHandler = function(key, val, d) {
      return (typeof(val) === "string") ? self.makeTerminalHandler(key, val, d) : self.makeTreeHandler(key, val, d);
    };
    
    self.makeTerminalHandler = function(key, name, d) {
      return function(e) {
        e.preventDefault();
        
        var next = self.makeTerminalElement(name);
        self.stack.push(next);
        d.parentNode.appendChild(next);

        $(d).animate({"margin-left": -400}, self.speed);
        
        self.crumbs.push(key);
        self.breadcrumbs.html(self.crumbs.join(" / "));
        return false;
      };
    };
    
    self.makeTerminalElement = function(name) {
      var e = document.createElement("div");
      e.setAttribute("class", "tree-result");
      e.innerHTML = name;
      return e;
    };

    self.makeTreeHandler = function(key, t, d) {
      return function(e) {
        e.preventDefault();
        
        var next = self.make(t);
        self.stack.push(next);
        d.parentNode.appendChild(next);

        $(d).animate({"margin-left": -400}, self.speed);
        
        self.crumbs.push(key);
        self.breadcrumbs.html(self.crumbs.join(" / "));
        return false;
      };
    };
    
    self.speed = 400;
    self.crumbs = [];
    self.stack = [self.make(self.data)];
    
    self.content.append(self.stack[0]);
    
  }
  
  return {
    create: function(data, content, breadcrumbs) { return new Tree(data, content, breadcrumbs); }
  };
  
})();
