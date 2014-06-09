$(document).on("ready",function() {
	pintMain();
});

$(window).on('beforeunload', function() {
	console.log("leaving...");
});

function pintMain() {
  pdata = new PintData();
  pbus = new PintBus();
  pbus.init( pdata.busOpen, pdata );
}
