var selectionTree = (function() {

  function Tree(data, content, breadcrumbs, callback) {
    var self = this;
    
    self.content = $(content);
    self.breadcrumbs = $(breadcrumbs);
    self.data = data;
    self.width = content.parent().width();
    self.speed = self.width; // magical, but good.
    self.callback = callback;
    self.crumbs = [];
    self.stack = [];
        
    self.goback = function() {
      if (self.stack.length > 1) {
        var d = self.stack.pop();
        var n = self.stack[self.stack.length - 1];
        $(d).animate({"margin-left": self.width}, self.speed, function() { n.parentNode.removeChild(d); });
        $(n).animate({"margin-left": 0}, self.speed);  
        self.crumbs.pop();
        self.breadcrumbs.html(self.crumbs.join(" / "));
      }
      return false;
    };
    
    self.reset = function() {
      self.crumbs = [];
      self.stack = [self.make(self.data)];
      self.content.empty().append(self.stack[0]);
      return false;
    };
    
    self.makeHandler = function(key, val, d) {
      return function(e) {
        e.preventDefault();
        
        var createNext = (typeof(val) === "string") ? self.makeTerminalElement : self.make;
        var next = createNext(val);
        self.stack.push(next);
        d.parentNode.appendChild(next);

        $(d).animate({"margin-left": -self.width}, self.speed);
        
        self.crumbs.push(key);
        self.breadcrumbs.html(self.crumbs.join(" / "));
        return false;
      };
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
    
    self.makeTerminalElement = function(name) {
      if (self.callback) self.callback(name);
      var e = document.createElement("div");
      e.setAttribute("class", "tree-result");
      e.innerHTML = name;
      return e;
    };

    self.stack.push(self.make(self.data));
    self.content.append(self.stack[0]);
    
  }
  
  return {
    create: function(data, content, breadcrumbs, callback) { return new Tree(data, content, breadcrumbs, callback); }
  };
  
})();
