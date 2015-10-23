;(function() {
  "use strict";

  window.lupapisteApp = new LUPAPISTE.App({startPage: "bulletins",
                                           allowAnonymous: true,
                                           showUserMenu: false,
                                           componentPages: ["bulletin"]});
  $(function() {
    lupapisteApp.domReady();

    var components = [
        {name: "bulletins"},
        {name: "application-bulletins"},
        {name: "application-bulletins-list"},
        {name: "load-more-application-bulletins"},
        {name: "application-bulletin"},
        {name: "bulletins-search"},
        {name: "autocomplete-municipalities"},
        {name: "autocomplete-states"}];

    _.forEach(components, function(component) {
      ko.components.register(component.name, {
        viewModel: LUPAPISTE[_.capitalize(_.camelCase(component.model ? component.model : component.name + "Model"))],
        template: { element: (component.template ? component.template : component.name + "-template")}
      });
    });

    $("#bulletins").applyBindings({});
  });
})();
