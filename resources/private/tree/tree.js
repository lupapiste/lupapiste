var selectionTree = (function() {

  function defaultContentFactory(name) {
    var e = document.createElement("div");
    e.setAttribute("class", "tree-result");
    e.innerHTML = name;
    return e;
  }

  function Tree(data, content, breadcrumbs, callback, contentFactory) {
    var self = this;
    
    self.data = data;
    self.content = $(content);
    self.breadcrumbs = $(breadcrumbs);
    self.callback = callback;
    self.makeTerminalElement = contentFactory || defaultContentFactory;

    self.width = content.parent().width();
    self.speed = self.width; // magical, but good.
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
        
        self.crumbs.push(key);
        self.breadcrumbs.html(self.crumbs.join(" / "));
        
        var terminal = typeof(val) === "string";
        var next = terminal ? self.makeTerminalElement(val) : self.make(val);
        self.stack.push(next);
        d.parentNode.appendChild(next);
        var done = (terminal && self.callback) ? self.callback.bind(self, val) : null;
        $(d).animate({"margin-left": -self.width}, self.speed, done);
        return false;
      };
    };
        
    self.make = function(t) {
      var d = document.createElement("div");
      d.setAttribute("class", "tree-magic");
      for (var key in t) d.appendChild( self.makeLink(key, t[key], d) );

      if (self.stack.length > 0) {
        var link = document.createElement("a");
        link.innerHTML = loc("tree.back");
        link.href = "#";
        link.onclick = self.goback;
        d.appendChild(link);
      }
      
      if (self.stack.length > 1) {
        var link = document.createElement("a");
        link.innerHTML = loc("tree.start");
        link.href = "#";
        link.onclick = self.reset;
        d.appendChild(link);
      }

      return d;
    };

    self.makeLink  = function(key, val, d) {
      var link = document.createElement("a");
      var lkey = "tree." + (self.crumbs.length == 0 ? key : self.crumbs.join(".") + "." + key) + ".name";
      link.innerHTML = loc(lkey);
      link.href = "#";
      link.onclick = self.makeHandler(key, val, d);
      return link;
    };
    
    self.stack.push(self.make(self.data));
    self.content.append(self.stack[0]);
    
  }
  
  return {
    create: function(data, content, breadcrumbs, callback, contentFactory) {
      return new Tree(data, content, breadcrumbs, callback, contentFactory);
    }
  };
  
})();
