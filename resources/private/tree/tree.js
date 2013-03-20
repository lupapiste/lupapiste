;(function($) {
  "use strict";

  function nop() { }

  function Tree(context, args) {
    var self = this;

    args = args || {};
    var defaultTemplate = args.template || $(".default-tree-template");
    
    var titleTemplate = args.title || $(".tree-title", defaultTemplate);
    var contentTemplate = args.content || $(".tree-content", defaultTemplate);
    var navTemplate = args.content || $(".tree-nav", defaultTemplate);
    
    self.linkTemplate = args.link || $(".tree-link", defaultTemplate);
    self.lastTemplate = args.last || $(".tree-last", defaultTemplate);

    self.onSelect = args.onSelect || nop;
    self.data = [];
    self.width = args.width || context.width();
    self.speed = args.speed || self.width / 2;
    self.moveLeft  = {"margin-left": "-=" + self.width};
    self.moveRight = {"margin-left": "+=" + self.width};
    
    function findTreeData(target) {
      if (!target) return null;
      var data = target.data("tree-link-data");
      return data ? data : findTreeData(target.parent())
    }
    
    self.clickGo = function(e) {
      var target = $(e.target),
          link = findTreeData(target);
      if (!link) return false;
      var selectedLink = link[0],
          nextElement = link[1],
          next = _.isArray(nextElement) ? self.makeLinks(nextElement) : self.makeFinal(nextElement);
      self.model.stack.push(selectedLink);
      self.stateNop().content.append(next).animate(self.moveLeft, self.speed, self.stateGo);
      return false;
    };
    
    self.goBack = function() {
      if (self.model.stack().length < 1) return false;
      if (self.atFinal) {
        self.atFinal = false;
        self.onSelect(null);
      }
      self.stateNop();
      self.model.stack.pop();
      self.content.animate(self.moveRight, self.speed, function() {
        self.stateGo();
        $(".tree-page", self.content).filter(":last").remove();
      });
      return self;
    };
    
    self.setClickHandler = function(handler) { self.clickHandler = handler; return self; }
    self.stateGo = _.partial(self.setClickHandler, self.clickGo);
    self.stateNop = _.partial(self.setClickHandler, nop);

    self.makeFinal = function(data) {
      self.onSelect(data);
      self.atFinal = true;
      return self.lastTemplate.clone().addClass("tree-page").css("width", self.width + "px").applyBindings(data);
    }
    
    self.makeLinks = function(data) {
      return _.reduce(data, self.appendLink, $("<div>").addClass("tree-page").css("width", self.width + "px"));
    };
    
    self.appendLink = function(div, linkData) {
      var link = self.linkTemplate
        .clone()
        .data("tree-link-data", linkData)
        .applyBindings(linkData[0]);
      return div.append(link);
    };
    
    self.reset = function(data) {
      self.stateNop();
      self.data = data;
      self.model.stack.removeAll();
      self.content.empty().css("margin-left", "" + self.width + "px").append(self.makeLinks(data)).animate(self.moveLeft, self.speed, self.stateGo);
      return self;
    };
    
    self.model = {
      stack: ko.observableArray([]),
      goBack: self.goBack,
      goStart: function() { self.reset(self.data); return false; }
    };

    self.content = contentTemplate.clone().click(function(event) {
      var e = getEvent(event);
      e.preventDefault();
      e.stopPropagation();
      self.clickHandler(e);
      return false;
    });
    
    context
      .append(titleTemplate.clone())
      .append(self.content)
      .append(navTemplate.clone())
      .applyBindings(self.model);
  }
  
  $.fn.selectTree = function(arg) {
    return new Tree(this, arg);
  };

  var api = {};
  api.reset = function(data) { self.reset(data); return api; };
  api.back = function() { self.goBack(); return api; };
  return api;
  
})(jQuery);
