package es.sebas1705.nanodemo.feature.lab.memory

/**
 * A scripted conversation with facts stated EARLY and a final question about them. The assistant lines are
 * written in advance, so a run measures only what each memory strategy keeps, not what a model improvises.
 * The conversations are long enough (about 1,200 tokens) to overflow the 1k-token engines and the small fixed
 * budgets, but fit the larger ones. The content is model-facing data (Spanish, the demo language), not UI text.
 */
class MemoryScenario(
    val id: String,
    val turns: List<Turn>,
    val question: String,
    /** Each expected fact lists acceptable spellings; see [recallScore]. */
    val expected: List<List<String>>,
)

object MemoryScenarios {

    val trip = MemoryScenario(
        id = "trip",
        turns = listOf(
            Turn("Hola, soy Marta. Estoy organizando un viaje a Lisboa con mi perro Luna.", "Encantado, Marta. Lisboa es una ciudad estupenda para ir con perro: hay muchos miradores, parques y terrazas donde los perros son bienvenidos."),
            Turn("Salimos el 14 de marzo y volvemos el 21.", "Anotado: del 14 al 21 de marzo, siete noches. Es una buena duración para recorrer la ciudad con calma y hacer alguna excursión a Sintra o Cascais."),
            Turn("Somos tres personas y el presupuesto es de 1200 euros en total.", "Con 1200 euros entre tres se puede hacer bastante si cuidáis el alojamiento y las comidas; los vuelos y el hotel serán la mayor parte del gasto."),
            Turn("Soy vegetariana, así que busco restaurantes sin carne.", "Lisboa tiene muy buena oferta vegetariana, desde tascas con platos de verduras hasta restaurantes especializados en cocina vegetal en el Chiado y Alvalade."),
            Turn("¿Qué barrio me recomiendas para dormir?", "Alfama es pintoresco, pero tiene muchas cuestas y calles empedradas; Baixa es más cómodo y céntrico, aunque algo más ruidoso por las noches."),
            Turn("Prefiero algo tranquilo, la verdad.", "Entonces Campo de Ourique es una buena opción: es residencial, tiene mercado, cafés y buena conexión de tranvía con el centro histórico."),
            Turn("¿Hay que reservar con antelación en marzo?", "En marzo no suele hacer falta reservar con mucha antelación, salvo los fines de semana y los restaurantes pequeños más populares del centro."),
            Turn("Vale. ¿Y cómo me muevo por la ciudad?", "El metro y los tranvías cubren casi todo; con perro conviene el metro fuera de hora punta, y el tranvía 28 suele ir lleno, así que mejor evitarlo."),
            Turn("¿Se puede entrar con perro en los museos?", "Depende del museo; casi todos limitan el acceso de animales, pero los jardines, los miradores y muchas terrazas sí admiten perros sin problema."),
            Turn("Tengo ganas de probar los pasteles de nata.", "Imprescindibles. Los de Belém son los más famosos, y algunas pastelerías del centro ofrecen versiones con menos azúcar o sin lácteos."),
            Turn("¿Qué tiempo hará en esas fechas?", "Unos 17 grados de máxima y 10 de mínima, con sol por las mañanas y algo de lluvia ocasional por las tardes, típico de marzo en Lisboa."),
            Turn("Perfecto, llevaré paraguas.", "Buena idea, y calzado cómodo y con suela que agarre bien para las cuestas y los adoquines, que resbalan cuando llueve."),
            Turn("¿Merece la pena una excursión a Sintra?", "Totalmente. Se llega en tren desde Rossio en unos 40 minutos; el Palacio da Pena y el de Monserrate son los más vistosos, y conviene ir temprano."),
            Turn("¿Y la playa? Hará frío, claro.", "Para bañarse sí, pero pasear por Cascais o por la costa de Estoril en marzo es muy agradable, y hay playas donde los perros pueden correr sueltos fuera de temporada."),
            Turn("Bien. ¿Algún mirador bonito para el atardecer?", "El de Santa Luzia, el de Graça y el de Santa Catarina son los clásicos; este último es más informal y suele haber música y gente joven."),
            Turn("Me gustaría ver algo de fado, ¿dónde?", "En Alfama y el Bairro Alto hay casas de fado auténticas; mejor reservar en una pequeña y evitar las que cobran entrada muy cara para turistas."),
            Turn("¿Es cara la comida allí?", "Es razonable: un menú del día cuesta entre 10 y 14 euros, y los pasteles, el café y la fruta son baratos. Las zonas más turísticas suben bastante los precios."),
            Turn("¿Hace falta cambiar moneda?", "No, Portugal usa el euro. Las tarjetas se aceptan casi en todas partes, aunque conviene llevar algo de efectivo para mercados y tascas pequeñas."),
            Turn("Genial. ¿Algún consejo final?", "Descarga un mapa sin conexión, lleva un cargador portátil y reserva con tiempo el transporte al aeropuerto si tu vuelo sale muy temprano."),
            Turn("Gracias, lo apunto todo.", "De nada. Si quieres, en cuanto lo tengas más cerrado te ayudo a montar el itinerario día a día."),
        ),
        question = "Para hacer la lista de la compra del viaje: ¿cómo se llama mi perro, a qué ciudad vamos y cuántos euros tenemos de presupuesto?",
        expected = listOf(listOf("Luna"), listOf("Lisboa"), listOf("1200", "1.200", "1 200")),
    )

    val project = MemoryScenario(
        id = "project",
        turns = listOf(
            Turn("Soy Pablo, líder técnico del proyecto Orquídea. Lanzamos en septiembre.", "Entendido, Pablo: proyecto Orquídea con lanzamiento en septiembre. Con esa fecha conviene fijar cuanto antes el alcance mínimo de la primera versión."),
            Turn("El equipo son cinco desarrolladores y una diseñadora llamada Irene.", "Cinco desarrolladores e Irene en diseño. Es un equipo equilibrado para un producto móvil, siempre que el diseño vaya un paso por delante del desarrollo."),
            Turn("Usamos Kotlin en el móvil y Postgres en el servidor.", "Kotlin y Postgres es una combinación sólida y bien documentada; asegúrate de tener migraciones versionadas desde el principio."),
            Turn("El mayor riesgo ahora mismo es el rendimiento del login.", "El login suele ser un buen sitio para empezar a medir: tiempos de respuesta del servidor, cifrado y la primera carga de datos tras autenticarse."),
            Turn("Hemos decidido no soportar tablets en la primera versión.", "Tiene sentido para acotar el alcance; deja los layouts preparados para adaptarse más adelante, así el coste de añadirlos después será menor."),
            Turn("Las revisiones de código son obligatorias antes de fusionar.", "Perfecto, eso protege la calidad. Procura que las revisiones sean pequeñas y rápidas para que no se conviertan en un cuello de botella."),
            Turn("Cada martes hacemos una demo interna a las 11.", "Los martes a las 11, anotado. Una demo semanal obliga a tener algo funcional siempre y da visibilidad al resto de la empresa."),
            Turn("¿Cómo organizo las tareas de la semana?", "Agrúpalas por riesgo y deja el rendimiento del login arriba; después las que desbloquean a otros compañeros, y por último las mejoras menores."),
            Turn("Buena idea. Y para la deuda técnica, ¿qué hago?", "Reserva un porcentaje fijo de cada sprint y apúntala en el mismo tablero que el resto del trabajo, con una estimación, para que no se acumule sin verse."),
            Turn("Vale, un 15% me parece razonable.", "Un 15% fijo es una práctica habitual y suficiente para mantener el código sano sin frenar la entrega de funcionalidad nueva."),
            Turn("¿Cómo mido el progreso hacia septiembre?", "Usa un burn-up de funcionalidades críticas en lugar de horas: muestra a la vez cuánto está hecho y cómo crece o se recorta el alcance."),
            Turn("Tiene sentido. ¿Y las pruebas?", "Pruebas unitarias para la lógica, pruebas de integración para el servidor y un pequeño conjunto de pruebas de interfaz solo para los flujos críticos."),
            Turn("¿Conviene automatizar el despliegue?", "Sí, cuanto antes: un pipeline que compile, pase las pruebas y publique una versión interna evita muchos errores manuales antes del lanzamiento."),
            Turn("¿Qué hago con las notificaciones push?", "Déjalas para una segunda iteración si no son críticas; implica permisos, canales y pruebas en muchos dispositivos que consumen tiempo."),
            Turn("De acuerdo. ¿Cómo comunico los cambios a dirección?", "Un informe semanal de una página: avances, riesgos y decisiones pendientes, en lenguaje no técnico y con un indicador claro de si vais en fecha."),
            Turn("Perfecto, lo prepararé el viernes.", "Muy bien. Si quieres, te ayudo a redactar la plantilla del informe para que cada semana solo haya que actualizar los datos."),
        ),
        question = "Redacta una línea para el informe semanal con el nombre del proyecto, el mes de lanzamiento y el mayor riesgo actual.",
        expected = listOf(listOf("Orquídea", "Orquidea"), listOf("septiembre"), listOf("login")),
    )

    val all: List<MemoryScenario> = listOf(trip, project)
}
