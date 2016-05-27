;(function() {
  "use strict";

  var components = [
    {name: "calendar-view"},
    {name: "reservation-slot-create-bubble"}
  ];

  $(function() {
    _.forEach(components, function(component) {
      var opts = {
                   viewModel: LUPAPISTE[_.capitalize(_.camelCase(component.model ? component.model : component.name + "Model"))],
                   template: { element: (component.template ? component.template : component.name + "-template")},
                   synchronous: component.synchronous ? true : false
                 };
      ko.components.register(component.name, opts);
    });
  });

})();
